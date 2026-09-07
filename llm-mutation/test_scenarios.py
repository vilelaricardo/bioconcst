#!/usr/bin/env python3
"""
Standalone prototype: tests whether an LLM can (1) break a genuine tie between
equally-unexplored race candidates in a way that beats random choice, and
(2) recognize a structural mutual-exclusion (semantic triage) without being
told about it - against real source code from BioConcST's own benchmarks,
via the qwen2.5-coder:7b model running in Ollama on the Legion machine.

No GA involved - this only exercises the proposed mutation/triage function
in isolation, repeated N times per scenario, to get real frequency data
instead of a single anecdotal sample.
"""

import json
import re
import urllib.request
from collections import Counter

OLLAMA_URL = "http://192.168.68.61:11434/api/generate"
MODEL = "qwen2.5-coder:7b"
REPEATS = 20

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

TOKEN_RING_SOURCE = '''
public class TokenRingSlave {
    public static void main(String args[]) throws Exception {
        processId = Integer.parseInt(args[0]);
        operation = Integer.parseInt(args[1]);   // 0 or 1, fixed for this whole run
        direction = Integer.parseInt(args[2]);
        processAmount = Integer.parseInt(args[3]);
        // ... socket/port setup omitted ...
        Semaphore tokenToProducer = new Semaphore(0);
        tokenToProducer.release();
        Buffer sharedObject = new Buffer();
        server.receive(receivePacket);
        int sharedVariable = Integer.parseInt(new String(receivePacket.getData()).trim());
        sharedObject.setSharedObject(sharedVariable);
        if (operation == 0) {
            sharedObject.setSharedIntIncrement();
        } else {
            sharedObject.setSharedIntMultiplie();
        }
        Producer producer1 = new Producer();
        producer1.setProducer(tokenToProducer, operation);
        producer1.setSharedObject(sharedObject);
        producer1.start();
        // (producer2, producer3 identical, omitted)
        producer1.join();
    }
}

public class Producer extends Thread {
    private Semaphore tokenToProducer;
    private int operation;

    public void run() {
        if (operation == 0) {
            tokenToProducer.acquireUninterruptibly();  // edge Producer#run:0
            object.setSharedIntIncrement();
            tokenToProducer.release();                 // edge Producer#run:1
        }
        if (operation == 1) {
            tokenToProducer.acquireUninterruptibly();  // edge Producer#run:2
            object.setSharedIntMultiplie();
            tokenToProducer.release();                 // edge Producer#run:3
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


def run_tie_break_scenario():
    print("=" * 70)
    print("CENARIO 1 (desempate): 011_parallel_sieve, Slave1.receive")
    print("Candidatos empatados (0 tentativas cada): Master, Slave2")
    print("=" * 70)

    prompt = f"""You are analyzing a Java concurrency benchmark for test generation.

Source code (Slave.java, one process in a parallel sieve of Eratosthenes):
{SLAVE_JAVA}

Context: In this benchmark, Slave1's `socket.receive(receivePacket)` call could
legitimately be fed by either process "Master" or process "Slave2" (both are
allowed senders per the declared topology). Across all test runs so far,
NEITHER candidate has ever been forced yet (0 attempts each - a genuine tie).

Task: pick exactly ONE of the two candidates to force on the next test run,
to make progress on exploring this race. Answer with EXACTLY this format,
nothing else:
CHOICE: <Master or Slave2>
REASON: <one short sentence>
"""

    counts = Counter()
    invalid = 0
    for i in range(REPEATS):
        try:
            response = call_ollama(prompt)
        except Exception as e:
            print(f"  [{i}] ERROR calling model: {e}")
            invalid += 1
            continue
        match = re.search(r"CHOICE:\s*(Master|Slave2)", response, re.IGNORECASE)
        if match:
            choice = match.group(1).capitalize() if match.group(1).lower() == "master" else "Slave2"
            counts[choice] += 1
            reason = re.search(r"REASON:\s*(.+)", response)
            print(f"  [{i}] -> {choice}  ({reason.group(1).strip() if reason else '?'})")
        else:
            invalid += 1
            print(f"  [{i}] UNPARSEABLE: {response[:150]!r}")

    print()
    print(f"Resultado ({REPEATS} chamadas): {dict(counts)}  | inválidas: {invalid}")
    print(f"Esperado por acaso puro (aleatório 50/50): ~{REPEATS/2:.0f}/{REPEATS/2:.0f}")
    print()


def run_semantic_triage_scenario():
    print("=" * 70)
    print("CENARIO 2 (triagem semantica): 003_token_ring_file")
    print("Pergunta: os ramos operation==0 e operation==1 coexistem na mesma execucao?")
    print("=" * 70)

    prompt = f"""You are analyzing a Java concurrency benchmark for test generation.

Source code:
{TOKEN_RING_SOURCE}

Question: `operation` is a single command-line argument, fixed for the entire
run and shared identically by every process (TokenRingSlave and all its
Producer threads). Two pairs of synchronization points are marked with
comments: Producer#run:0/run:1 (guarded by operation==0) and Producer#run:2/
run:3 (guarded by operation==1).

Is it possible for a SEM_ACQUIRE at Producer#run:0 and a SEM_ACQUIRE at
Producer#run:2 to BOTH occur within the same single execution of this
program? Answer with EXACTLY this format, nothing else:
ANSWER: <YES or NO>
REASON: <one short sentence>
"""

    counts = Counter()
    invalid = 0
    for i in range(REPEATS):
        try:
            response = call_ollama(prompt)
        except Exception as e:
            print(f"  [{i}] ERROR calling model: {e}")
            invalid += 1
            continue
        match = re.search(r"ANSWER:\s*(YES|NO)", response, re.IGNORECASE)
        if match:
            answer = match.group(1).upper()
            counts[answer] += 1
            reason = re.search(r"REASON:\s*(.+)", response)
            print(f"  [{i}] -> {answer}  ({reason.group(1).strip() if reason else '?'})")
        else:
            invalid += 1
            print(f"  [{i}] UNPARSEABLE: {response[:150]!r}")

    print()
    print(f"Resultado ({REPEATS} chamadas): {dict(counts)}  | inválidas: {invalid}")
    print("Resposta correta (achamos por análise manual): NO - operation é um único valor fixo,")
    print("os dois ramos if(operation==0)/if(operation==1) nunca são ambos verdadeiros.")
    print()


if __name__ == "__main__":
    run_tie_break_scenario()
    run_semantic_triage_scenario()
