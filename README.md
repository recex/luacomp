# Lua Computers (Forge 1.20.1)

Mod de computadores programáveis em Lua, inspirado no ComputerCraft: Tweaked.

## Como rodar
1. Java 17.
2. Gradle wrapper: copie `gradlew`, `gradlew.bat` e a pasta `gradle/` do MDK do Forge 1.20.1,
   ou rode `gradle wrapper --gradle-version 8.8`.
3. `./gradlew runClient` para testar, `./gradlew build` para gerar o .jar (em `build/libs/`).

> O projeto foi escrito sem poder compilar. No primeiro build, pequenos erros de compilação podem aparecer.

## Blocos (aba Redstone do criativo)
| Bloco | Para que serve |
|---|---|
| Computador | GUI de terminal 51x19, Lua, shell e editor |
| Monitor | Telas que se juntam em retângulos até 8x6; clique direito gera `monitor_touch` |
| Modem sem fio | Mensagens por canal com alcance (config), base do `rednet` |
| Modem com fio + Cabo | Rede de cabos: expõe os periféricos ligados a cada modem como `tipo_N` |
| Alto-falante | `playNote`, `playSound` |
| Sensor de entidades | `getEntities`, `getPlayers` |
| Câmera | `capture(largura, altura)` devolve uma matriz de cores |
| Leitor de disquete + Disquete | Clique com o disquete insere, mão vazia ejeta. O disquete vira a pasta `disk` |
| Turtle | Computador que anda, cava, coloca blocos, pega/solta itens |
| Impressora | `newPage`, `write`, `setPageTitle`, `endPage`; papel e corantes por clique ou hopper. Clique com a página impressa mostra o texto no chat |

Periférico encostado no computador aparece pelo lado (`left`, `top`...). Modem com fio encostado no computador
(placa presa nele) liga o computador à rede de cabos.

## Atalhos na GUI
Ctrl+T terminar programa - Ctrl+R ligar/reiniciar - Ctrl+Shift+S desligar - Ctrl+V colar - Esc fecha

## Shell
`ls cd mkdir rm cp mv cat edit echo clear lua id label time reboot shutdown peripherals`
`wget <url> [arq]` · `pastebin get|run|put` · `store [list|install nome]`

Editor (`edit arq.lua`): realce de sintaxe Lua, Ctrl+S salva, Ctrl+Q sai, Ctrl+V cola texto grande.

## APIs Lua
`term` (+`term.redirect`) · `fs` · `os` (timers, eventos, `getEnergy`) · `redstone`/`rs` · `colors` · `keys`
`peripheral` (`getNames isPresent getType getMethods call wrap find`) · `http` (`get post request checkURL`) · `rednet`
`parallel` (`waitForAny waitForAll`) · `textutils` (`serialize unserialize serializeJSON unserializeJSON formatTime urlEncode`)
`require` · `turtle` (só em turtles) · `colors.combine/subtract/test/toBlit/fromBlit`
`settings` · `term.setPaletteColor/getPaletteColor` · `window` (create, reposition, setVisible, redraw...) · `paintutils` (drawPixel/Line/Box/FilledBox/Image, loadImage) · `fs.find`

Eventos: `key key_up char paste terminate redstone timer http_success http_failure
peripheral peripheral_detach modem_message monitor_touch disk disk_eject`

## Exemplos
```lua
-- monitor
local mon = peripheral.find("monitor")
mon.setTextScale(1)
mon.clear()
mon.setCursorPos(1, 1)
mon.write("Olá, monitor!")

-- redirecionar o terminal para o monitor
local old = term.redirect(mon)
print("isso aparece no monitor")
term.redirect(old)

-- rede sem fio
rednet.open("top")            -- lado do modem
rednet.broadcast("oi", "chat")
local id, msg = rednet.receive("chat", 5)

-- alto-falante
local sp = peripheral.find("speaker")
sp.playNote("harp", 1, 12)

-- baixar e rodar do pastebin (no shell):  pastebin run <código>
```

## Turtles
Shift+clique com um item na mão coloca no inventário (16 slots); Shift+clique com a mão vazia devolve o primeiro item.
Hoppers e canos também enxergam o inventário. A frente do turtle aponta para onde o jogador olhava ao colocar.
Combustível: `turtle.refuel()` queima o item do slot selecionado (carvão = 80 movimentos). Desative em `turtle.fuelEnabled`.
Ao quebrar, o computador mantém ID, nome, inventário e combustível no item.

`forward back up down turnLeft turnRight · dig digUp digDown · place placeUp placeDown · attack* · detect* inspect*
drop* suck* · select getSelectedSlot getItemCount getItemSpace getItemDetail · getFuelLevel getFuelLimit refuel`

## Disquetes
Disquete = redstone + papel. Cada disquete tem um ID e uma pasta no mundo (125 KB), que aparece em `disk` (`disk2`...).
Evento `disk` ao inserir, `disk_eject` ao remover. O leitor é o periférico `drive` (`getMountPath`, `getDiskLabel`, `setDiskLabel`, `ejectDisk`...).

## Exemplos prontos
Na pasta `examples/` do projeto: `tunnel.lua` (turtle), `chat.lua` (rednet), `camera_view.lua`, `disco.lua`.
Copie para o computador com `pastebin`, `wget` ou colando no `edit` (Ctrl+V).

## Configuração (`config/luacomp-common.toml`)
- `http`: liga/desliga, lista de domínios permitidos (padrão: pastebin e github), tempo limite, tamanho máximo,
  chave do Pastebin (só no servidor), URL do índice da loja. Endereços locais/privados são sempre bloqueados.
- `computer.cpuTimeoutMs`: tempo máximo sem ceder a vez.
- `energy`: consumo de FE por tick e por CPU usada (desligado por padrão).
- `peripherals`: alcance do modem, modem entre dimensões, raio do sensor.
- `turtle`: combustível (ligado, inicial, limite), atraso das ações, permitir cavar.

Formato do índice da loja: arquivo Lua `return { {name="x", url="https://...", desc="..."}, ... }`.

## Limitações conhecidas
- Memória: não há cota por computador. Existe limite de tempo de CPU, limite de `string.rep` e uma trava que aborta o script quando a memória da JVM passa de 92%
- `http`: a checagem de IP privado resolve o DNS antes de conectar (sem fixar o IP)
- Turtle: não tem animação de movimento, não tem ferramentas equipáveis nem `craft`; `place` não calcula
  orientação para todos os blocos; cavar/colocar não consulta proteções de terreno de outros mods
- Monitores grandes em escala pequena mandam muitos dados; atualização limitada a ~7 por segundo
- Câmera usa as cores de mapa dos blocos (visual simples, sem entidades)
- Não existem: áudio de disquete
- Texturas provisórias
- O projeto nunca foi compilado: o primeiro build pode apontar pequenos erros
