Integrantes:

Fernanda Salgueiro Franceschini

Seção Implementação:

O código entregue apresenta todas as funcionalidades requisitadas, nas classes MemoryManager e ProcessManager e CPU.

Todos os comandos de interface seguem com os mesmos nomes requisitados.

Adaptações no código base foram realizadas para melhor organização dos dados.

O código é inicializado com um programa NOOP, que tem a função de manter a CPU ocupada sem executar nenhuma operação significativa, 
garantindo que o sistema tenha um processo válido em execução enquanto outros processos não estão prontos ou enquanto o escalonador gerencia a fila de processos.

Ao criar um processo ele não é automáticamente inicializado, sendo necessário o comando "exec <id>" ou "execAll"

Foi criado o fatorialTrap e o fibonacciTRAP para testar as operações de I/O

Seção Testes:

TESTE 1 -> Lotação da memória principal com processos alocados

[TESTE 1] Na classe VM.java, altere MEMORY_SIZE para 64 e PARTITION_SIZE para 16.
[TESTE 1] Execute a aplicação no terminal e digite 5 vezes consecutivas "cria fatorial", na quinta vez um aviso de indisponibilidade será exibido.
[TESTE 1] Digite "desaloca 3" para liberar uma partição e na sequência digite "cria fatorial" novamente para adicionar um processo.
[TESTE 1] Observe que as partições funcionam corretamente para gerenciar espaços livres e ocupados da memória.

TESTE 2 -> Processo maior que uma partição

[TESTE 2] Execute a aplicação no terminal e digite "cria PC", em seguida execute "dump 1".
[TESTE 2] Observe nos dados do PCB que o processo está listando mais de uma partição.
[TESTE 2] Ou seja, multiplas páginas estão sendo usadas, diferentemente do particionamento fixo.

TESTE 3 -> Escalonamento de Processos

[TESTE 3] Execute a aplicação no terminal e digite "cria fibonacci10".
[TESTE 3] Execute a aplicação no terminal e digite "cria fatorial".
[TESTE 3] Digite "dump 2" e observe que ainda não foi executado em "[SYS] 46:  [ DATA, -1, -1, -1  ]".
[TESTE 3] Digite "dump 3" e observe que ainda não foi executado em "[SYS] 58:  [ DATA, -1, -1, -1  ]".
[TESTE 3] Digite "traceOn" para habilitar o debug da CPU.
[TESTE 3] Digite "execAll" para executar o processo e ver eles se intercalando, interrupções de TIMEOUT serão dadas e a CPU vai escalonar outro processo.
[TESTE 3] Digite "dumpM 16" para observar os resultados, "[SYS] 46:  [ DATA, -1, -1, 55  ]", ou seja, a execução foi feita e o resultado 55 foi entregue.
[TESTE 3] Digite "dump 48 64" para observar os resultados, "[SYS] 58:  [ DATA, -1, -1, 24  ]", ou seja, a execução foi feita e o resultado 24 foi entregue.

TESTE 4 -> Alocação e desalocação de memória sobre mesma partição

[TESTE 4] Execute a aplicação no terminal e digite "cria progMinimo".
[TESTE 4] Digite "dump 0 30", observe os valores ocupados na memória.
[TESTE 4] Digite "desaloca 1" e em seguida digite "cria fatorial".
[TESTE 4] Digite "dump 0 30" e observe que os valores do processo progMinimo foram removidos da partição e substituidos pelo processo fatorial.

TESTE 5 -> Paginação dos processos

[TESTE 5] Na classe VM.java, altere MEMORY_SIZE para 128 e PARTITION_SIZE para 16.
[TESTE 5] Execute a aplicação no terminal e digite "cria fatorial" 5 vezes.
[TESTE 5] Digite "desaloca 2" e "desaloca 4" em seguida.
[TESTE 5] Digite "cria PC".
[TESTE 5] Digite "dump 6" e observe nas informações do PCB.
[TESTE 5] Os dados devem apresentar as páginas [1,3,6,7], demonstrando que o sistema de paginação é funcional e não necessita ser sequêncial.

TESTE 6/7 -> Chamadas de Sistema (INPUT,OUTPUT)
[TESTE 6] -> Execute a aplicação no terminal e digite "cria fibonacciTRAP", em seguida digite "dump 2"
[TESTE 6] -> Execute a aplicação no terminal e digite "cria fibonacci10"
[TESTE 6] -> Digite "execAll" para rodar os processos e perceba que o fibonacciTrap vai lançar uma interrupção de I/O
[TESTE 6] -> O programa do "fibonacci10" continua a executar enquanto o processo "fibonacciTRAP" esta bloqueado esperando uma entrada
[TESTE 6] -> Digite 10 no terminal e perceba que o processo do "fibonacciTRAP" é desbloqueado e volta a rodar. 

[TESTE 7] → Execute a aplicação no terminal e digite: "cria fatorial" umas duas vezes
[TESTE 7] → Execute a aplicação no terminal e digite: "cria fatorialTRAP"
[TESTE 7] → Execute a aplicação no terminal e digite: "cria fibonacci10"
[TESTE 7] → Em seguida, digite: "traceOn" para ver os logs
[TESTE 7] → Digite: "execAll" para iniciar a execução dos processos, perceba que os processos vão escalonar entre si.
[TESTE 7] → O processo "fatorialTRAP" inicia e executa normalmente até encontrar a instrução TRAP.
[TESTE 7] → Na instrução TRAP, o processo faz uma chamada de sistema do tipo TRAP OUTPUT, usando R8 = 2 para indicar a operação e R9 = 18 para indicar o endereço do resultado (o fatorial calculado).
[TESTE 7] → O sistema operacional gera uma interrupção do tipo IO_REQUEST, bloqueando o processo "fatorialTRAP" enquanto aguarda a saída ser concluída.
[TESTE 7] → Enquanto isso, outros processos continuam executando normalmente (exemplo: ID 4 executa instruções ADD).
[TESTE 7] → Após a operação de I/O ser concluída, aparece no terminal a saída: [IO-3] 5040 — que é o valor do fatorial calculado (7! = 5040).
[TESTE 7] → O sistema gera a interrupção IO_RETURN bloqueando o processo que estava executando e desbloqueando o processo "fatorialTRAP", que retoma sua execução.
[TESTE 7] → volta a rodar o processo 4 que havia sido interrompido.
