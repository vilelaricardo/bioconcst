#!/usr/bin/env python3
"""
Same bias-control methodology as test_bias_control.py, applied to a
SELECTION decision instead of mutation: given two individuals tied on
every fitness dimension (a genuine Pareto tie - same coverage, same
coveredCount, real numbers from the sieve benchmark's own replay bundle),
which one should survive to the next generation?

If this shows the same position-driven pattern as the mutation test, it
confirms the failure mode is about "forced binary choice with no real
differentiating signal" in general, not specific to mutation.
"""

import json
import re
import urllib.request
from collections import Counter

OLLAMA_URL = "http://192.168.68.61:11434/api/generate"
MODEL = "qwen2.5-coder:7b"
REPEATS = 15


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

    prompt = f"""You are the selection operator of a genetic algorithm that generates
test inputs for a concurrent program (a parallel sieve of Eratosthenes benchmark).

Two individuals from the current population are tied on every fitness
dimension (a genuine Pareto tie - neither dominates the other):

Individual "{first}": coverage = 77.78% (7/9 required sync edges), distance = 0.0
Individual "{second}": coverage = 77.78% (7/9 required sync edges), distance = 0.0

Task: pick exactly ONE of the two individuals to survive into the next
generation. Answer with EXACTLY this format, nothing else:
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
    results["nomes_reais_ordem_original"] = run_condition(
        "genotipos reais, ordem original", "Individual_7960", "Individual_3699")
    results["nomes_reais_ordem_invertida"] = run_condition(
        "genotipos reais, ordem invertida", "Individual_3699", "Individual_7960")
    results["rotulos_neutros_mesma_ordem"] = run_condition(
        "rotulos neutros, mesma ordem", "CandidateA", "CandidateB")

    print("=" * 70)
    print("RESUMO FINAL")
    print("=" * 70)
    for label, counts in results.items():
        print(f"{label}: {dict(counts)}")
