#!/usr/bin/env python3
"""
Bias-control follow-up to test_scenarios.py's Scenario 1 (tie-break).
Original result: Master 17/20, Slave2 3/20 - strongly non-random, but the
reasoning text never cited anything code-specific, suggesting name/position
bias rather than genuine discrimination. This isolates which.
"""

import json
import re
import urllib.request
from collections import Counter

OLLAMA_URL = "http://192.168.68.61:11434/api/generate"
MODEL = "qwen2.5-coder:7b"
REPEATS = 15

SLAVE_JAVA = '''
public class Slave {
    public static void main(String args[]) throws Exception {
        // ... sieve setup omitted ...
        int x = 0, loop = 0, iteration = 1;
        while (loop != 1) {
            boolean isLast = HelperClass.thisProcessIsLast(processId, iteration, numberOfProcesses);
            int value = processId / iteration + (processId % iteration > 0 ? 1 : 0);
            if (value % 2 == 0 && !isLast) {
                // receive data of other slave
                socket.receive(receivePacket);
                String[] result = new String(receivePacket.getData()).trim().split(" ");
                for (int j = 0; j < result.length; j++) {
                    x = Integer.parseInt(result[j].trim());
                    out = HelperClass.makeMessagePrimes(builder, x);
                }
            } else if (value % 2 != 0) {
                int toSend = ((((processId / iteration) - 1) + (((processId % iteration) > 0) ? 1 : 0)) * iteration);
                String hostIP = HelperClass.readRemoteIP(toSend);
                InetAddress remoteIP = InetAddress.getByName(hostIP);
                int remotePort = HelperClass.readRemotePort(toSend);
                sendBuffer = out.getBytes();
                DatagramPacket datagram = new DatagramPacket(sendBuffer, sendBuffer.length, remoteIP, remotePort);
                socket.send(datagram);
                break;
            }
            iteration = iteration * 2;
            loop = numberOfProcesses / iteration + (numberOfProcesses % iteration > 0 ? 1 : 0);
        }
    }
}
'''.strip()


def call_ollama(prompt: str) -> str:
    payload = json.dumps({
        "model": MODEL,
        "prompt": prompt,
        "stream": False,
        "options": {"num_predict": 200, "temperature": 0.7},
    }).encode("utf-8")
    req = urllib.request.Request(OLLAMA_URL, data=payload, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=120) as resp:
        return json.loads(resp.read().decode("utf-8"))["response"]


def run_condition(label: str, first: str, second: str):
    print("=" * 70)
    print(f"CONDICAO: {label}  (ordem no prompt: {first} primeiro, {second} segundo)")
    print("=" * 70)

    prompt = f"""You are analyzing a Java concurrency benchmark for test generation.

Source code (Slave.java, one process in a parallel sieve of Eratosthenes):
{SLAVE_JAVA}

Context: In this benchmark, Slave1's `socket.receive(receivePacket)` call could
legitimately be fed by either process "{first}" or process "{second}" (both are
allowed senders per the declared topology). Across all test runs so far,
NEITHER candidate has ever been forced yet (0 attempts each - a genuine tie).

Task: pick exactly ONE of the two candidates to force on the next test run,
to make progress on exploring this race. Answer with EXACTLY this format,
nothing else:
CHOICE: <{first} or {second}>
REASON: <one short sentence>
"""

    counts = Counter()
    invalid = 0
    pattern = re.compile(rf"CHOICE:\s*({re.escape(first)}|{re.escape(second)})", re.IGNORECASE)
    for i in range(REPEATS):
        try:
            response = call_ollama(prompt)
        except Exception as e:
            print(f"  [{i}] ERROR: {e}")
            invalid += 1
            continue
        match = pattern.search(response)
        if match:
            raw = match.group(1)
            choice = first if raw.lower() == first.lower() else second
            counts[choice] += 1
            print(f"  [{i}] -> {choice}")
        else:
            invalid += 1
            print(f"  [{i}] UNPARSEABLE: {response[:120]!r}")

    print()
    print(f"Resultado ({label}): {dict(counts)}  | inválidas: {invalid}")
    print()
    return counts


if __name__ == "__main__":
    results = {}
    results["nomes_reais_ordem_original"] = run_condition("nomes reais, ordem original", "Master", "Slave2")
    results["nomes_reais_ordem_invertida"] = run_condition("nomes reais, ordem invertida", "Slave2", "Master")
    results["rotulos_neutros_mesma_ordem"] = run_condition("rotulos neutros A/B, mesma ordem de Master/Slave2", "ProcessoA", "ProcessoB")

    print("=" * 70)
    print("RESUMO FINAL")
    print("=" * 70)
    for label, counts in results.items():
        print(f"{label}: {dict(counts)}")
