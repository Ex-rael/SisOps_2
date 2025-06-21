/**
 * Trabalho Prático 2B - Sistemas Operacionais - PUCRS 2025/1
 * Professor: Fernando Luís Dotti
 *
 * Objetivo:
 * Implementar memória virtual com paginação, simulação de disco (IO), tratamento de page fault
 * e gerenciamento de processos com mudança de estado conforme acesso à memória.
 *
 * Funcionalidades implementadas:
 *
 * 1. Paginação sob demanda:
 *    - Ao criar um processo, apenas a primeira página é carregada na memória.
 *    - A tabela de páginas do processo mantém o mapeamento página/quadro.
 *
 * 2. Tratamento de Page Fault:
 *    - Quando uma página é acessada e não está em memória, ocorre um page fault.
 *    - O Gerente de Memória tenta alocar um quadro livre.
 *    - Se não houver quadros livres, uma página é escolhida para ser vitimada (política simples).
 *    - A página vítima é salva no disco (simulado pela classe Programs).
 *    - A nova página é carregada do disco (se já foi usada antes) ou do programa original.
 *    - O processo é bloqueado durante o carregamento e desbloqueado após o fim do IO.
 *
 * 3. Disco (Programs.java):
 *    - Simula a memória secundária, armazenando páginas vitimadas.
 *    - Permite salvar e carregar páginas de/para a memória.
 *    - Diferencia páginas novas de páginas previamente vitimadas.
 *
 * 4. Gerenciamento de Processos (ProcessManager.java):
 *    - Cada processo pode estar nos estados READY, RUNNING ou BLOCKED.
 *    - Um processo que sofre page fault é movido para BLOCKED.
 *    - Após fim do IO, ele é movido de volta para READY.
 *    - Escalonamento simples (FIFO/Round Robin) está implementado.
 *
 * 5. Interrupções e Continuação:
 *    - Após carregar a nova página, simula-se uma interrupção que retira o processo de BLOCKED.
 *    - Outro processo é escalonado enquanto isso ocorre.
 *
 * 6. Testes:
 *    - Os mesmos testes da primeira etapa funcionam.
 *    - A memória e os quadros ocupados podem ser visualizados com logs durante a execução.
 *
 * Conclusão:
 * O projeto implementa completamente os requisitos da fase 2B, simulando um sistema com memória
 * virtual por paginação, gerenciamento de IO, vitimização de páginas e escalonamento de processos
 * com estados sincronizados à execução do sistema.
 */


package Software;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Semaphore;

import Hardware.CPU.CPU;
import Hardware.CPU.CPU.Interrupts;
import Hardware.MainMemory.Memory;
import Hardware.SecondaryMemory.Programs;
import Software.ProcessManager.PCB;
import VirtualMachine.VM;

public class OS {

	public MemoryManager memoryManager;
	public ProcessManager processManager;
	public Routines routines;
	public Shell shell;
	public Console console;
	public Programs progs;

	public boolean consoleIO;
	public Queue<Integer> consoleInput;
	public Semaphore shellSemaphore;
	public Semaphore consoleSemaphore;

	public OS(Memory memory, CPU cpu, Programs progs) {
		this.memoryManager = new MemoryManager(memory);
		this.processManager = new ProcessManager(cpu, memoryManager, this);
		this.routines = new Routines(this.processManager, this.memoryManager);
		this.progs = progs;

		this.consoleIO = false;
		this.consoleInput = new LinkedList<>();
		this.shell = new Shell();
		this.console = new Console();

		this.shellSemaphore = new Semaphore(1);
		this.consoleSemaphore = new Semaphore(0);

		this.processManager.setNoop();
		cpu.scheduler.schedulerSemaphore.release();

	}

	public class Routines {

		private ProcessManager processManager;
		private MemoryManager memoryManager;

		public Routines(ProcessManager processManager, MemoryManager memoryManager) {
			this.processManager = processManager;
			this.memoryManager = memoryManager;
		}

		public void input(int processId, int memoryAddress, int inputValue) {

			PCB pcb = processManager.pcbList.get(processId);

			int position = memoryManager.translate(memoryAddress, pcb.pages, processId);

			memoryManager.memory.m[position].p = inputValue;

		}

		public void output(int processId, int memoryAddress) {

			PCB pcb = processManager.pcbList.get(processId);

			int position = memoryManager.translate(memoryAddress, pcb.pages, processId);

			System.out.println("[IO-" + processId + "] " + memoryManager.memory.m[position].p);

		}

		public void shmalloc(int processId, int key) {

			PCB pcb = processManager.pcbList.get(processId);
			if (pcb == null) {
				processManager.cpu.irpt.add(Interrupts.INVALID_ADDRESS);
				return;
			}

			int availableFrame = memoryManager.getNextAvailableFrame();
			if (availableFrame < 0) {
				processManager.cpu.irpt.add(Interrupts.OUT_OF_MEMORY);
				return;
			}

			int[] newPartitions = new int[pcb.pages.length + 1];

			for (int i = 0; i < pcb.pages.length; i++) {
				newPartitions[i] = pcb.pages[i];
			}

			newPartitions[newPartitions.length - 1] = availableFrame;
			pcb.pages = newPartitions;
			processManager.cpu.pages = newPartitions;

			memoryManager.linkFrameToKey(availableFrame, key);
		}

		public void shmref(int processId, int key) {

			PCB pcb = processManager.pcbList.get(processId);
			if (pcb == null) {
				processManager.cpu.irpt.add(Interrupts.INVALID_ADDRESS);
				return;
			}

			int sharedFrame = memoryManager.getSharedFrameByKey(key);
			if (sharedFrame == -1) {
				processManager.cpu.irpt.add(Interrupts.INVALID_VALUE);
				return;
			}

			int[] newPartitions = new int[pcb.pages.length + 1];

			for (int i = 0; i < pcb.pages.length; i++) {
				newPartitions[i] = pcb.pages[i];
			}

			newPartitions[newPartitions.length - 1] = sharedFrame;
			pcb.pages = newPartitions;
			processManager.cpu.pages = newPartitions;

		}

	}

	public enum ReturnCode {
		DUMP, PROC_EXEC_OK, PROC_EXEC_FAIL, PROC_DEALLOC, PROC_CREATE, PROC_NO_MEMORY,
		PROC_NO_PARTITION, PROC_NOT_FOUND, PROC_BUSY, MEM_OUT_OF_RANGE
	}

	public class Shell extends Thread {

		public void run() {
			try {

				System.out.println("[SYS] Informe um comando:");

				while (true) {

					String data = VM.input.nextLine();

					if (consoleIO) {

						try {
							shellSemaphore.acquire();
							consoleInput.add(Integer.parseInt(data));
							consoleSemaphore.release();
						} catch (Exception e) {
							System.out.println("[IO] Valor informado não é numérico");
						}

					}

					else {

						String[] inputList = data.split(" ");

						switch (inputList[0]) {
							case "cria":
								switch (inputList[1]) {
									case "fibonacci10":
										status(processManager.create(progs.fibonacci10));
										break;
									case "progMinimo":
										status(processManager.create(progs.progMinimo));
										break;
									case "fatorial":
										status(processManager.create(progs.fatorial));
										break;
									case "fatorialTRAP":
										status(processManager.create(progs.fatorialTRAP));
										break;
									case "fibonacciTRAP":
										status(processManager.create(progs.fibonacciTRAP));
										break;
									case "PC":
										status(processManager.create(progs.PC));
										break;
									default:
										System.out.println("[SYS] Programa não encontrado");
										break;
								}
								break;
							case "listaProcessos":
								System.out.println(
										"[SYS] IDs dos processos inicializados: " + processManager.pcbList.keySet());
								break;
							case "dump":
								try {
									status(processManager.dump(Integer.parseInt(inputList[1])));
								} catch (Exception e) {
									System.out.println("[SYS] ID de processo informado não é numérico");
								}
								break;
							case "desaloca":
								try {
									status(processManager.deallocate(Integer.parseInt(inputList[1])));
								} catch (Exception e) {
									System.out.println("[SYS] ID de processo informado não é numérico");
								}
								break;
							case "dumpM":
								try {
									status(processManager.dump(Integer.parseInt(inputList[1]),
											Integer.parseInt(inputList[2])));
								} catch (Exception e) {
									System.out.println("[SYS] Valores de memória não são numéricos");
								}
								break;
							case "executa":
								try {
									processManager.execute(Integer.parseInt(inputList[1]));
								} catch (Exception e) {
									System.out.println("[SYS] ID de processo informado não é numérico");
								}
								break;
							case "execAll":
								processManager.executeAll();
								break;
							case "traceOn":
								processManager.traceOn();
								System.out.println("[SYS] Debug ativado");
								break;
							case "traceOff":
								processManager.traceOff();
								System.out.println("[SYS] Debug desativado");
								break;
							case "exit":
								System.exit(0);
								break;
							default:
								System.out.println("[SYS] Comando inválido");
								break;
						}

						System.out.println("[SYS] Informe um comando:");

					}

				}

			} catch (Exception e) {
				e.printStackTrace();
			}

		}

	}

	public class Console extends Thread {

		public void run() {

			while (true) {

				try {

					processManager.cpu.consoleSemaphore.acquire();
					PCB requestProcess = processManager.pcbList.get(processManager.cpu.ioQueue.poll());
					processManager.cpu.ioSemaphore.release();

					if (requestProcess.registers[8] == 1) {
						input(requestProcess);
					} else if (requestProcess.registers[8] == 2) {
						output(requestProcess);
					}

				} catch (Exception e) {
					e.printStackTrace();
				}

			}

		}

		private void input(PCB requestProcess) throws Exception {
			consoleIO = true;

			System.out.println("[IO-" + requestProcess.id + "] Informe um valor numérico:");

			consoleSemaphore.acquire();
			routines.input(requestProcess.id, requestProcess.registers[9], consoleInput.poll());
			consoleIO = false;

			shellSemaphore.release();

			processManager.cpu.irptSemaphore.acquire();

			processManager.cpu.irpt.add(Interrupts.IO_RETURN);
			processManager.cpu.unblock.add(requestProcess.id);

			processManager.cpu.irptSemaphore.release();

			System.out.println("[SYS] Informe um comando:");
		}

		private void output(PCB requestProcess) throws Exception {
			routines.output(requestProcess.id, requestProcess.registers[9]);

			processManager.cpu.irptSemaphore.acquire();

			processManager.cpu.irpt.add(Interrupts.IO_RETURN);
			processManager.cpu.unblock.add(requestProcess.id);

			processManager.cpu.irptSemaphore.release();

		}

	}

	public void status(ReturnCode returnCode) {
		switch (returnCode) {
			case DUMP:
				System.out.println("[SYS] Dump realizado");
				break;
			case PROC_EXEC_OK:
				System.out.println("[SYS] Processo executado com sucesso");
				break;
			case PROC_EXEC_FAIL:
				System.out.println("[SYS] Processo falhou durante sua execução");
				break;
			case PROC_DEALLOC:
				System.out.println("[SYS] Processo desalocado com sucesso");
				break;
			case PROC_CREATE:
				System.out.println("[SYS] Processo criado com sucesso");
				break;
			case PROC_NO_MEMORY:
				System.out.println("[SYS] Processo maior que a memória total disponível, abortando operação");
				break;
			case PROC_NO_PARTITION:
				System.out.println("[SYS] Processo não encontrou partições disponíveis, abortando operação");
				break;
			case PROC_NOT_FOUND:
				System.out.println("[SYS] Processo não encontrado");
				break;
			case PROC_BUSY:
				System.out.println("[SYS] Processo ocupado");
				break;
			case MEM_OUT_OF_RANGE:
				System.out.println("[SYS] Valores fora do alcance da memória, de 0 a " + VM.MEMORY_SIZE);
				break;
		}
	}

}
