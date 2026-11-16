-- shell.lua: shell, editor de texto e REPL do Lua Computers
local unpack = table.unpack
local function pack(...) return { n = select("#", ...), ... } end

local shell = {}
local cwd = ""
local history = {}
local running = true
local commands = {}
local runSource, runProgram

local aliases = {
  dir = "ls", list = "ls", type = "cat", del = "rm", delete = "rm", cls = "clear",
  copy = "cp", move = "mv", rename = "mv", md = "mkdir", quit = "exit", periph = "peripherals",
}

local function resolve(path)
  if path:sub(1, 1) == "/" then return fs.combine("", path) end
  return fs.combine(cwd, path)
end

function shell.dir() return cwd end
function shell.setDir(p) cwd = fs.combine("", p) end
function shell.resolve(p) return resolve(p) end
function shell.exit() running = false end

local function colored(color, text)
  local old = term.getTextColor()
  term.setTextColor(color)
  print(text)
  term.setTextColor(old)
end

local keywords = {}
for word in ("and break do else elseif end false for function goto if in local nil not or repeat return then true until while"):gmatch("%a+") do
  keywords[word] = true
end

-- devolve uma cor para cada byte da linha (realce simples de Lua, linha a linha)
local function colorize(line)
  local cols, n, i = {}, #line, 1
  while i <= n do
    local c = line:sub(i, i)
    if line:sub(i, i + 1) == "--" then
      for k = i, n do cols[k] = colors.gray end
      break
    elseif c == '"' or c == "'" then
      local j = i + 1
      while j <= n do
        local d = line:sub(j, j)
        if d == "\\" then j = j + 2
        elseif d == c then break
        else j = j + 1 end
      end
      j = math.min(j, n)
      for k = i, j do cols[k] = colors.orange end
      i = j + 1
    elseif c:match("%d") and not line:sub(i - 1, i - 1):match("[%w_]") then
      local j = i
      while j <= n and line:sub(j, j):match("[%w%.]") do j = j + 1 end
      for k = i, j - 1 do cols[k] = colors.purple end
      i = j
    elseif c:match("[%a_]") then
      local j = i
      while j <= n and line:sub(j, j):match("[%w_]") do j = j + 1 end
      local word = line:sub(i, j - 1)
      local col = colors.white
      if keywords[word] then col = colors.lightBlue
      elseif line:sub(j, j) == "(" then col = colors.yellow end
      for k = i, j - 1 do cols[k] = col end
      i = j
    else
      cols[i] = colors.white
      i = i + 1
    end
  end
  return cols
end

-- ---------------------------------------------------------------- editor
local function edit(path)
  local lines = {}
  if fs.exists(path) then
    if fs.isDir(path) then printError("Isso é uma pasta.") return end
    local h = fs.open(path, "r")
    local line = h.readLine()
    while line do lines[#lines + 1] = line; line = h.readLine() end
    h.close()
  end
  if #lines == 0 then lines[1] = "" end

  local w, h = term.getSize()
  local cx, cy = 1, 1
  local sx, sy = 0, 0
  local modified, status, quitArmed, ctrl = false, "", false, false
  local highlight = path:match("%.lua$") ~= nil

  local function fixScroll()
    if cy - sy < 1 then sy = cy - 1 end
    if cy - sy > h - 1 then sy = cy - (h - 1) end
    if cx - sx < 1 then sx = cx - 1 end
    if cx - sx > w then sx = cx - w end
  end

  local function redraw()
    fixScroll()
    term.setBackgroundColor(colors.black)
    term.setTextColor(colors.white)
    for r = 1, h - 1 do
      local line = lines[sy + r]
      term.setCursorPos(1, r)
      local view = line and line:sub(sx + 1, sx + w) or ""
      if highlight and line and #view > 0 then
        local cols = colorize(line)
        local pos = 1
        while pos <= #view do
          local col = cols[sx + pos] or colors.white
          local e = pos
          while e < #view and (cols[sx + e + 1] or colors.white) == col do e = e + 1 end
          term.setTextColor(col)
          term.write(view:sub(pos, e))
          pos = e + 1
        end
        term.setTextColor(colors.white)
        term.write(string.rep(" ", w - #view))
      else
        term.write(view .. string.rep(" ", w - #view))
      end
    end
    term.setCursorPos(1, h)
    term.setBackgroundColor(colors.gray)
    local info = (modified and "*" or "") .. fs.getName(path) .. "  L" .. cy .. ":C" .. cx
    local help = "^S salvar  ^Q sair"
    local bar = status ~= "" and status or (info .. string.rep(" ", math.max(1, w - #info - #help)) .. help)
    term.write((bar .. string.rep(" ", w)):sub(1, w))
    term.setBackgroundColor(colors.black)
    term.setCursorPos(cx - sx, cy - sy)
  end

  local function clampX()
    if cx > #lines[cy] + 1 then cx = #lines[cy] + 1 end
  end

  local function insertText(text)
    text = text:gsub("\r\n", "\n"):gsub("\r", "\n"):gsub("\t", "  ")
    local pos = 1
    while true do
      local nl = text:find("\n", pos, true)
      local part = text:sub(pos, (nl or (#text + 1)) - 1)
      local line = lines[cy]
      lines[cy] = line:sub(1, cx - 1) .. part .. line:sub(cx)
      cx = cx + #part
      if not nl then break end
      local cur = lines[cy]
      lines[cy] = cur:sub(1, cx - 1)
      table.insert(lines, cy + 1, cur:sub(cx))
      cy = cy + 1
      cx = 1
      pos = nl + 1
    end
    modified = true
  end

  local function save()
    local f, err = fs.open(path, "w")
    if not f then status = "Erro: " .. tostring(err) return end
    f.write(table.concat(lines, "\n"))
    f.close()
    modified = false
    status = "Salvo!"
  end

  term.clear()
  term.setCursorBlink(true)
  redraw()

  while true do
    local ev, a = os.pullEventRaw()
    -- a mensagem da barra some na próxima tecla "de verdade" (Ctrl sozinho não conta)
    if ev == "char" or ev == "paste" or (ev == "key" and a ~= keys.leftCtrl and a ~= keys.rightCtrl) then
      status = ""
    end
    if ev == "terminate" then
      break
    elseif ev == "char" and not ctrl then
      insertText(a)
      quitArmed = false
    elseif ev == "paste" then
      insertText(a)
      quitArmed = false
    elseif ev == "key_up" then
      if a == keys.leftCtrl or a == keys.rightCtrl then ctrl = false end
    elseif ev == "key" then
      if a == keys.leftCtrl or a == keys.rightCtrl then
        ctrl = true
      elseif ctrl and a == keys.s then
        save()
      elseif ctrl and a == keys.q then
        if modified and not quitArmed then
          status = "Há alterações! Ctrl+Q de novo para sair sem salvar."
          quitArmed = true
        else
          break
        end
      elseif a == keys.enter then
        insertText("\n")
      elseif a == keys.tab then
        insertText("  ")
      elseif a == keys.backspace then
        if cx > 1 then
          lines[cy] = lines[cy]:sub(1, cx - 2) .. lines[cy]:sub(cx)
          cx = cx - 1
          modified = true
        elseif cy > 1 then
          local prev = lines[cy - 1]
          lines[cy - 1] = prev .. lines[cy]
          table.remove(lines, cy)
          cy = cy - 1
          cx = #prev + 1
          modified = true
        end
      elseif a == keys.delete then
        if cx <= #lines[cy] then
          lines[cy] = lines[cy]:sub(1, cx - 1) .. lines[cy]:sub(cx + 1)
          modified = true
        elseif cy < #lines then
          lines[cy] = lines[cy] .. lines[cy + 1]
          table.remove(lines, cy + 1)
          modified = true
        end
      elseif a == keys.left then
        if cx > 1 then cx = cx - 1 elseif cy > 1 then cy = cy - 1; cx = #lines[cy] + 1 end
      elseif a == keys.right then
        if cx <= #lines[cy] then cx = cx + 1 elseif cy < #lines then cy = cy + 1; cx = 1 end
      elseif a == keys.up then
        if cy > 1 then cy = cy - 1; clampX() end
      elseif a == keys.down then
        if cy < #lines then cy = cy + 1; clampX() end
      elseif a == keys.home then
        cx = 1
      elseif a == keys["end"] then
        cx = #lines[cy] + 1
      elseif a == keys.pageUp then
        cy = math.max(1, cy - (h - 1)); clampX()
      elseif a == keys.pageDown then
        cy = math.min(#lines, cy + (h - 1)); clampX()
      end
    end
    redraw()
  end

  term.setCursorBlink(false)
  term.setBackgroundColor(colors.black)
  term.setTextColor(colors.white)
  term.clear()
  term.setCursorPos(1, 1)
end

-- ---------------------------------------------------------------- comandos
commands.help = function()
  colored(colors.yellow, "Comandos:")
  print("ls cd mkdir rm cp mv cat edit echo clear lua")
  print("id label time reboot shutdown exit")
  print("peripherals  - lista periféricos conectados")
  print("wget <url> [arq]   pastebin get|run|put   store")
  print("Programas: digite o nome de um arquivo .lua")
  colored(colors.lightGray, "Ctrl+T termina o programa, Ctrl+R reinicia.")
end

commands.ls = function(a)
  local path = resolve(a[1] or "")
  if not fs.exists(path) then printError("Não existe: " .. (a[1] or "")) return end
  if not fs.isDir(path) then print(fs.getName(path)) return end
  local w = term.getSize()
  local dirs, files = {}, {}
  for _, n in ipairs(fs.list(path)) do
    if fs.isDir(fs.combine(path, n)) then dirs[#dirs + 1] = n else files[#files + 1] = n end
  end
  local function out(n, col)
    local x = term.getCursorPos()
    if x > 1 and x + #n >= w then print() end
    term.setTextColor(col)
    write(n .. "  ")
  end
  for _, n in ipairs(dirs) do out(n, colors.green) end
  for _, n in ipairs(files) do out(n, colors.white) end
  term.setTextColor(colors.white)
  if #dirs + #files > 0 then print() end
end

commands.cd = function(a)
  local p = resolve(a[1] or "")
  if fs.isDir(p) then cwd = p else printError("Não é uma pasta: " .. (a[1] or "")) end
end

commands.mkdir = function(a)
  if not a[1] then printError("Uso: mkdir <pasta>") return end
  fs.makeDir(resolve(a[1]))
end

commands.rm = function(a)
  if not a[1] then printError("Uso: rm <caminho>") return end
  fs.delete(resolve(a[1]))
end

commands.mv = function(a)
  if not a[2] then printError("Uso: mv <de> <para>") return end
  fs.move(resolve(a[1]), resolve(a[2]))
end

commands.cp = function(a)
  if not a[2] then printError("Uso: cp <de> <para>") return end
  fs.copy(resolve(a[1]), resolve(a[2]))
end

commands.cat = function(a)
  if not a[1] then printError("Uso: cat <arquivo>") return end
  local h, err = fs.open(resolve(a[1]), "r")
  if not h then printError(err) return end
  local s = h.readAll()
  h.close()
  write(s)
  if s:sub(-1) ~= "\n" then print() end
end

commands.edit = function(a)
  if not a[1] then printError("Uso: edit <arquivo>") return end
  edit(resolve(a[1]))
end

commands.echo = function(a) print(table.concat(a, " ")) end
commands.clear = function() term.clear(); term.setCursorPos(1, 1) end
commands.id = function() print("Computador #" .. os.getComputerID()) end
commands.time = function() print(string.format("Hora: %.2f  Dia: %d", os.time(), os.day())) end
commands.reboot = function() os.reboot() end
commands.shutdown = function() os.shutdown() end
commands.exit = function() running = false end

commands.label = function(a)
  if a[1] then
    os.setComputerLabel(table.concat(a, " "))
  else
    print(os.getComputerLabel() or "Sem nome")
  end
end

commands.lua = function()
  colored(colors.yellow, "REPL do Lua. Use exit() para sair.")
  local alive = true
  local env = setmetatable({ exit = function() alive = false end }, { __index = _G })
  local replHistory = {}
  while alive do
    term.setTextColor(colors.lightBlue)
    write("lua> ")
    term.setTextColor(colors.white)
    local ok, line = pcall(read, nil, replHistory)
    if not ok then print() break end
    if line ~= "" then
      replHistory[#replHistory + 1] = line
      local f, err = load("return " .. line, "=lua", "t", env)
      if not f then f, err = load(line, "=lua", "t", env) end
      if not f then
        printError(err)
      else
        local res = pack(pcall(f))
        if not res[1] then
          printError(res[2])
        else
          for i = 2, res.n do print(tostring(res[i])) end
        end
      end
    end
  end
end


local function pasteCode(text)
  return text:match("pastebin%.com/raw/(%w+)") or text:match("pastebin%.com/(%w+)") or text
end

local function download(url)
  local h, err = http.get(url)
  if not h then return nil, err end
  local body = h.readAll()
  h.close()
  return body
end

local function saveFile(path, content)
  local f, err = fs.open(path, "w")
  if not f then return false, err end
  f.write(content)
  f.close()
  return true
end

commands.wget = function(a)
  if not a[1] then printError("Uso: wget <url> [arquivo]") return end
  local clean = a[1]:gsub("%?.*$", "")
  local name = a[2] or clean:match("([^/]+)$") or "download"
  local body, err = download(a[1])
  if not body then printError("Falhou: " .. tostring(err)) return end
  local ok, serr = saveFile(resolve(name), body)
  if ok then print("Salvo em " .. name .. " (" .. #body .. " bytes)") else printError(serr) end
end

commands.pastebin = function(a)
  local sub = a[1]
  if sub == "get" and a[2] and a[3] then
    local code = pasteCode(a[2])
    local body, err = download("https://pastebin.com/raw/" .. code)
    if not body then printError("Falhou: " .. tostring(err)) return end
    local ok, serr = saveFile(resolve(a[3]), body)
    if ok then print("Salvo em " .. a[3]) else printError(serr) end
  elseif sub == "run" and a[2] then
    local code = pasteCode(a[2])
    local body, err = download("https://pastebin.com/raw/" .. code)
    if not body then printError("Falhou: " .. tostring(err)) return end
    runSource(body, code, { unpack(a, 3) })
  elseif sub == "put" and a[2] then
    local path = resolve(a[2])
    local h = fs.open(path, "r")
    if not h then printError("Arquivo não encontrado: " .. a[2]) return end
    local content = h.readAll()
    h.close()
    local res, err = http.pastebinPut(fs.getName(path), content)
    if not res then printError("Falhou: " .. tostring(err)) return end
    local url = res.readAll()
    res.close()
    print("Enviado: " .. url)
  else
    print("Uso: pastebin get <código> <arquivo>")
    print("     pastebin run <código> [args]")
    print("     pastebin put <arquivo>")
  end
end

commands.store = function(a)
  local url = http.storeUrl()
  if url == "" then printError("A loja está desativada neste servidor.") return end
  local body, err = download(url)
  if not body then printError("Falhou: " .. tostring(err)) return end
  local fn = load(body, "=index", "t", {})
  local ok, list = false, nil
  if fn then ok, list = pcall(fn) end
  if not ok or type(list) ~= "table" then printError("Índice da loja inválido.") return end

  local sub = a[1] or "list"
  if sub == "list" then
    for _, e in ipairs(list) do
      term.setTextColor(colors.yellow)
      write(tostring(e.name))
      term.setTextColor(colors.white)
      print(" - " .. tostring(e.desc or ""))
    end
  elseif sub == "install" and a[2] then
    for _, e in ipairs(list) do
      if e.name == a[2] and type(e.url) == "string" then
        local code, derr = download(e.url)
        if not code then printError("Falhou: " .. tostring(derr)) return end
        local file = e.name .. ".lua"
        local ok2, serr = saveFile(resolve(file), code)
        if ok2 then print("Instalado: " .. file) else printError(serr) end
        return
      end
    end
    printError("Programa não encontrado: " .. a[2])
  else
    print("Uso: store [list]  |  store install <nome>")
  end
end

commands.peripherals = function()
  local names = peripheral.getNames()
  if #names == 0 then print("Nenhum periférico conectado.") return end
  for _, n in ipairs(names) do
    term.setTextColor(colors.yellow)
    write(n)
    term.setTextColor(colors.white)
    print("  (" .. tostring(peripheral.getType(n)) .. ")")
  end
end

-- ---------------------------------------------------------------- programas
local function findProgram(name)
  for _, p in ipairs({ resolve(name), resolve(name .. ".lua") }) do
    if fs.exists(p) and not fs.isDir(p) then return p end
  end
end

function runSource(src, name, args)
  local env = setmetatable({ shell = shell }, { __index = _G })
  local fn, lerr = load(src, "=" .. name, "t", env)
  if not fn then printError(lerr) return false end
  local ok, e = pcall(fn, unpack(args or {}))
  if not ok then
    printError(tostring(e))
    return false
  end
  return true
end

function runProgram(path, args)
  local h, err = fs.open(path, "r")
  if not h then printError(err) return false end
  local src = h.readAll()
  h.close()
  return runSource(src, fs.getName(path), args)
end

local function tokenize(line)
  local args, i = {}, 1
  while i <= #line do
    local c = line:sub(i, i)
    if c == " " then
      i = i + 1
    elseif c == '"' then
      local j = line:find('"', i + 1, true) or (#line + 1)
      args[#args + 1] = line:sub(i + 1, j - 1)
      i = j + 1
    else
      local j = line:find(" ", i, true) or (#line + 1)
      args[#args + 1] = line:sub(i, j - 1)
      i = j + 1
    end
  end
  return args
end

local function execute(line)
  local args = tokenize(line)
  local name = table.remove(args, 1)
  if not name then return end
  name = aliases[name] or name
  local cmd = commands[name]
  if cmd then
    local ok, err = pcall(cmd, args)
    if not ok then printError(tostring(err)) end
    return
  end
  local path = findProgram(name)
  if path then
    runProgram(path, args)
  else
    printError("Comando não encontrado: " .. name)
  end
end

function shell.run(...)
  execute(table.concat({ ... }, " "))
end

-- ---------------------------------------------------------------- início
colored(colors.yellow, "Lua Computers 0.1  (ID " .. os.getComputerID() .. ")")
print("Digite 'help' para ver os comandos.")

if fs.exists("startup.lua") then runProgram("startup.lua", {}) end

while running do
  term.setTextColor(colors.yellow)
  write("/" .. cwd .. "> ")
  term.setTextColor(colors.white)
  local ok, line = pcall(read, nil, history)
  if ok then
    if line ~= "" then
      history[#history + 1] = line
      execute(line)
    end
  else
    print()
    if tostring(line) ~= "Terminated" then printError(tostring(line)) end
  end
end
