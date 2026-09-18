#!/usr/bin/env python3
"""
Same scenario/ground-truth as test_rich_context.py (baseline: 75%, 15/20,
order-balanced) - now varying ONLY the prompt phrasing, to see whether
accuracy is robust to wording or whether some structures do meaningfully
better/worse. Each variant runs both label orders x 10 repeats (20 calls),
same as the baseline, for a fair comparison.
"""

import json
import re
import urllib.request
from collections import Counter

OLLAMA_URL = "http://192.168.68.61:11434/api/generate"
MODEL = "qwen2.5-coder:7b"
REPEATS_PER_ORDER = 10
CORRECT_ANSWER = "SlaveB"

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
        "options": {"num_predict": 350, "temperature": 0.7},
    }).encode("utf-8")
    req = urllib.request.Request(OLLAMA_URL, data=payload, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=120) as resp:
        return json.loads(resp.read().decode("utf-8"))["response"]


def variant_baseline(first, second):
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


def variant_chain_of_thought(first, second):
    return f"""You are the mutation operator of a genetic algorithm generating test
schedules for a concurrent Java program.

Source code:
{SOURCE}

Missing coverage: edge "Coordinator#main:2" (the odd-path branch) has never
been exercised. The ambiguous receive can be fed by "{first}" or "{second}".

Think step by step BEFORE answering:
Step 1: What value does {first} send (even or odd)? What path does that trigger?
Step 2: What value does {second} send (even or odd)? What path does that trigger?
Step 3: Which one reaches the still-missing edge Coordinator#main:2?

After your reasoning, end your response with EXACTLY this final line:
CHOICE: <{first} or {second}>
"""


def variant_terse(first, second):
    return f"""{SOURCE}

Missing edge: Coordinator#main:2 (odd-path). Ambiguous receive fed by "{first}" or "{second}".
Which one to force to hit the missing edge?
CHOICE: <{first} or {second}>
REASON: <short>
"""


def variant_debias_instruction(first, second):
    return f"""You are the mutation operator of a genetic algorithm generating test
schedules for a concurrent Java program.

Full source code:
{SOURCE}

Missing coverage: edge "Coordinator#main:2" (the odd-path branch) has never
been exercised. The ambiguous receive can be fed by "{first}" or "{second}".

IMPORTANT: base your answer ONLY on what each sender's code actually
computes. Do NOT base it on the order these names are listed, their
alphabetical order, or any naming convention - only on the arithmetic each
one performs.

Answer with EXACTLY this format, nothing else:
CHOICE: <{first} or {second}>
REASON: <one short sentence citing the actual computed value>
"""


VARIANTS = {
    "baseline (ja testado antes, referencia)": variant_baseline,
    "chain_of_thought": variant_chain_of_thought,
    "terse": variant_terse,
    "debias_instruction": variant_debias_instruction,
}


def extract_choice(response: str):
    # For chain-of-thought, the CHOICE line may come after reasoning text -
    # take the LAST match, since instructions say "end with".
    matches = re.findall(r"CHOICE:\s*(SlaveA|SlaveB)", response, re.IGNORECASE)
    if not matches:
        return None
    raw = matches[-1]
    return "SlaveA" if raw.lower() == "slavea" else "SlaveB"


def run_variant(name, builder):
    print("=" * 70)
    print(f"VARIANTE: {name}")
    print("=" * 70)
    overall = Counter()
    invalid_total = 0
    for first, second in [("SlaveA", "SlaveB"), ("SlaveB", "SlaveA")]:
        prompt = builder(first, second)
        for i in range(REPEATS_PER_ORDER):
            try:
                response = call_ollama(prompt)
            except Exception as e:
                print(f"  [{first[:1]}{second[:1]}-{i}] ERROR: {e}")
                invalid_total += 1
                continue
            choice = extract_choice(response)
            if choice is None:
                invalid_total += 1
                print(f"  [{first[:1]}{second[:1]}-{i}] UNPARSEABLE: {response[-150:]!r}")
                continue
            overall[choice] += 1
            mark = "OK" if choice == CORRECT_ANSWER else "X"
            print(f"  [{first} first, {i}] -> {choice} [{mark}]")

    total = sum(overall.values())
    correct = overall.get(CORRECT_ANSWER, 0)
    accuracy = (100 * correct / total) if total else 0.0
    print(f"\n{name}: {dict(overall)} | inválidas: {invalid_total} | acurácia: {correct}/{total} ({accuracy:.0f}%)\n")
    return accuracy, invalid_total


if __name__ == "__main__":
    results = {}
    for name, builder in VARIANTS.items():
        if name.startswith("baseline"):
            print("=" * 70)
            print(f"VARIANTE: {name} -- PULANDO execucao, ja temos 15/20 (75%) do teste anterior")
            print("=" * 70)
            results[name] = (75.0, 0)
            continue
        results[name] = run_variant(name, builder)

    print("=" * 70)
    print("RESUMO COMPARATIVO")
    print("=" * 70)
    for name, (acc, invalid) in results.items():
        print(f"  {name}: {acc:.0f}% de acerto (inválidas: {invalid})")
