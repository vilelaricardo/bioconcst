#!/usr/bin/env python3
"""
Follow-up to the bias-control tests: those used a deliberately information-
FREE tie (only labels, no code, no history) to isolate the bias mechanism
cleanly. This tests the opposite case - real code, a real derivable answer,
and enough context that a correct choice requires actually reading the
logic, not just comparing labels. If the model still ignores this and
falls back to position/lexicographic order, that's a much stronger negative
result. If it consistently gets it right, that's real, positive evidence.

Ground truth: SlaveB always computes an ODD value; the still-uncovered
required edge only fires on the odd branch. The correct choice is SlaveB,
and it is NOT the lexicographically or numerically smaller label, and its
position is varied across repeats to control for the bias we already found.
"""

import json
import re
import random
import urllib.request
from collections import Counter

OLLAMA_URL = "http://192.168.68.61:11434/api/generate"
MODEL = "qwen2.5-coder:7b"
REPEATS_PER_ORDER = 10

SOURCE = '''
public class Coordinator {
    public static void main(String[] args) throws Exception {
        DatagramSocket socket = new DatagramSocket();
        byte[] receiveBuffer = new byte[64];
        DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);

        // This receive can legitimately be fed by either SlaveA or SlaveB -
        // both are declared senders per the topology config.
        socket.receive(receivePacket);                                  // edge Coordinator#main:0
        int value = Integer.parseInt(new String(receivePacket.getData()).trim());

        if (value % 2 == 0) {
            System.out.println("even path");
            logEvenPath();                                              // edge Coordinator#main:1
        } else {
            System.out.println("odd path - triggers the aggregation step");
            logOddPath();                                               // edge Coordinator#main:2  <-- NEVER COVERED YET
        }
    }
}

public class SlaveA {
    public static void main(String[] args) throws Exception {
        int myShare = computeShare();       // always returns an even number in this benchmark's arithmetic
        send(myShare);                       // edge SlaveA#main:0
    }
}

public class SlaveB {
    public static void main(String[] args) throws Exception {
        int myShare = computeShare() + 1;   // always one more than SlaveA's even share -> always odd
        send(myShare);                       // edge SlaveB#main:0
    }
}
'''.strip()


def call_ollama(prompt: str) -> str:
    payload = json.dumps({
        "model": MODEL,
        "prompt": prompt,
        "stream": False,
        "options": {"num_predict": 250, "temperature": 0.7},
    }).encode("utf-8")
    req = urllib.request.Request(OLLAMA_URL, data=payload, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=120) as resp:
        return json.loads(resp.read().decode("utf-8"))["response"]


def build_prompt(first: str, second: str) -> str:
    return f"""You are the mutation operator of a genetic algorithm generating test
schedules for a concurrent Java program. Your job is to pick which sender
to force for an ambiguous receive point, in order to reach a specific
uncovered line of code.

Full source code of the three processes involved:
{SOURCE}

Coverage status: every required edge has been covered by previous test runs
EXCEPT "Coordinator#main:2" (the odd-path branch, inside logOddPath()) -
this edge has never been exercised yet, across many generations.

The ambiguous receive point is Coordinator's socket.receive(receivePacket)
(edge Coordinator#main:0), which can legitimately be fed by either "{first}"
or "{second}" per the declared topology.

Task: pick exactly ONE of the two senders to force on the next test run, in
order to make progress on covering the still-missing edge
Coordinator#main:2. Answer with EXACTLY this format, nothing else:
CHOICE: <{first} or {second}>
REASON: <one short sentence>
"""


def run():
    correct_answer = "SlaveB"  # sends an odd value -> triggers the uncovered odd-path edge
    orders = [("SlaveA", "SlaveB"), ("SlaveB", "SlaveA")]
    overall = Counter()
    for first, second in orders:
        print("=" * 70)
        print(f"ORDEM: {first} primeiro, {second} segundo (resposta correta: {correct_answer})")
        print("=" * 70)
        prompt = build_prompt(first, second)
        pattern = re.compile(r"CHOICE:\s*(SlaveA|SlaveB)", re.IGNORECASE)
        counts = Counter()
        invalid = 0
        for i in range(REPEATS_PER_ORDER):
            try:
                response = call_ollama(prompt)
            except Exception as e:
                print(f"  [{i}] ERROR: {e}")
                invalid += 1
                continue
            match = pattern.search(response)
            if match:
                choice = "SlaveA" if match.group(1).lower() == "slavea" else "SlaveB"
                counts[choice] += 1
                reason = re.search(r"REASON:\s*(.+)", response)
                mark = "CORRETO" if choice == correct_answer else "ERRADO"
                print(f"  [{i}] -> {choice} [{mark}]  ({reason.group(1).strip()[:100] if reason else '?'})")
            else:
                invalid += 1
                print(f"  [{i}] UNPARSEABLE: {response[:120]!r}")
        print(f"\nResultado: {dict(counts)} | inválidas: {invalid}\n")
        overall.update(counts)

    print("=" * 70)
    print("RESUMO GERAL (as duas ordens juntas)")
    print("=" * 70)
    total = sum(overall.values())
    correct = overall.get(correct_answer, 0)
    print(f"{dict(overall)}")
    print(f"Acertos (SlaveB, a resposta certa): {correct}/{total} ({100*correct/total:.0f}%)")
    print("Acaso puro (aleatório 50/50) esperaria: 50%")


if __name__ == "__main__":
    run()
