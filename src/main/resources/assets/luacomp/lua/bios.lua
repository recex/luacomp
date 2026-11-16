-- bios.lua: ambiente base de cada computador (roda dentro de uma coroutine)
local __sys = __sys
_G.__sys = nil

local function pack(...) return { n = select("#", ...), ... } end

local unpack = table.unpack or _G.unpack
if not unpack then
  function unpack(t, i, j)
    i = i or 1
    j = j or #t
    if i > j then return end
    return t[i], unpack(t, i + 1, j)
  end
end
table.unpack = unpack
table.pack = table.pack or pack

-- load só aceita texto (nada de bytecode) e usa _G por padrão
local _load = load
function load(chunk, name, mode, env)
  return _load(chunk, name or "=(load)", "t", env or _G)
end

-- ---------------------------------------------------------------- cores e teclas
colors = {
  white = 1, orange = 2, magenta = 4, lightBlue = 8, yellow = 16, lime = 32,
  pink = 64, gray = 128, lightGray = 256, cyan = 512, purple = 1024,
  blue = 2048, brown = 4096, green = 8192, red = 16384, black = 32768,
}
colors.grey, colors.lightGrey = colors.gray, colors.lightGray
colours = colors

keys = {
  backspace = 259, enter = 257, tab = 258, space = 32, up = 265, down = 264,
  left = 263, right = 262, home = 268, ["end"] = 269, delete = 261, insert = 260,
  pageUp = 266, pageDown = 267, leftShift = 340, rightShift = 344,
  leftCtrl = 341, rightCtrl = 345, leftAlt = 342, rightAlt = 346,
  minus = 45, equals = 61, comma = 44, period = 46, slash = 47,
}
local digits = { "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine" }
for i = 0, 9 do keys[digits[i + 1]] = 48 + i end
for c = 65, 90 do keys[string.char(c + 32)] = c end
for i = 1, 12 do keys["f" .. i] = 289 + i end
function keys.getName(code)
  for k, v in pairs(keys) do
    if v == code then return k end
  end
end

-- ---------------------------------------------------------------- eventos
function os.pullEventRaw(filter)
  return coroutine.yield(filter)
end

function os.pullEvent(filter)
  local ev = pack(coroutine.yield(filter))
  if ev[1] == "terminate" then error("Terminated", 0) end
  return unpack(ev, 1, ev.n)
end

function os.sleep(t)
  local id = os.startTimer(t or 0)
  repeat
    local _, tid = os.pullEvent("timer")
  until tid == id
end
sleep = os.sleep

function os.shutdown()
  __sys.shutdown()
  while true do coroutine.yield() end
end

function os.reboot()
  __sys.reboot()
  while true do coroutine.yield() end
end

-- ---------------------------------------------------------------- saída de texto
function write(text)
  text = tostring(text)
  local w, h = term.getSize()
  local lines = 0
  local function newline()
    local _, y = term.getCursorPos()
    if y >= h then
      term.scroll(1)
      term.setCursorPos(1, h)
    else
      term.setCursorPos(1, y + 1)
    end
    lines = lines + 1
  end
  while #text > 0 do
    local nl = text:find("\n", 1, true)
    local seg = nl and text:sub(1, nl - 1) or text
    text = nl and text:sub(nl + 1) or ""
    while #seg > 0 do
      local x = term.getCursorPos()
      local room = w - x + 1
      if room <= 0 then
        newline()
        room = w
      end
      term.write(seg:sub(1, room))
      seg = seg:sub(room + 1)
      if #seg > 0 then newline() end
    end
    if nl then newline() end
  end
  return lines
end

function print(...)
  local parts = {}
  for i = 1, select("#", ...) do parts[i] = tostring((select(i, ...))) end
  return write(table.concat(parts, "\t") .. "\n")
end

function printError(...)
  local old = term.getTextColor()
  term.setTextColor(colors.red)
  print(...)
  term.setTextColor(old)
end

-- ---------------------------------------------------------------- leitura de linha
function read(replaceChar, history, default)
  local w = term.getSize()
  local sx, sy = term.getCursorPos()
  local line = default or ""
  local pos = #line
  local hpos = history and (#history + 1) or nil
  term.setCursorBlink(true)

  local function redraw()
    local shown = replaceChar and string.rep(replaceChar:sub(1, 1), #line) or line
    local avail = w - sx + 1
    local off = math.max(0, pos - avail + 1)
    local view = shown:sub(off + 1, off + avail)
    term.setCursorPos(sx, sy)
    term.write(view .. string.rep(" ", avail - #view))
    term.setCursorPos(sx + pos - off, sy)
  end
  redraw()

  while true do
    local ev, a = os.pullEvent()
    if ev == "char" then
      line = line:sub(1, pos) .. a .. line:sub(pos + 1)
      pos = pos + #a
      redraw()
    elseif ev == "paste" then
      a = a:gsub("[\r\n].*", ""):gsub("\t", " ")
      line = line:sub(1, pos) .. a .. line:sub(pos + 1)
      pos = pos + #a
      redraw()
    elseif ev == "key" then
      if a == keys.enter then
        break
      elseif a == keys.backspace then
        if pos > 0 then
          line = line:sub(1, pos - 1) .. line:sub(pos + 1)
          pos = pos - 1
          redraw()
        end
      elseif a == keys.delete then
        if pos < #line then
          line = line:sub(1, pos) .. line:sub(pos + 2)
          redraw()
        end
      elseif a == keys.left then
        if pos > 0 then pos = pos - 1 redraw() end
      elseif a == keys.right then
        if pos < #line then pos = pos + 1 redraw() end
      elseif a == keys.home then
        pos = 0 redraw()
      elseif a == keys["end"] then
        pos = #line redraw()
      elseif (a == keys.up or a == keys.down) and history then
        if a == keys.up and hpos > 1 then hpos = hpos - 1 end
        if a == keys.down and hpos <= #history then hpos = hpos + 1 end
        line = history[hpos] or ""
        pos = #line
        redraw()
      end
    end
  end

  term.setCursorBlink(false)
  local _, h = term.getSize()
  if sy >= h then
    term.scroll(1)
    term.setCursorPos(1, h)
  else
    term.setCursorPos(1, sy + 1)
  end
  return line
end

-- ---------------------------------------------------------------- term.redirect (monitores)
local nativeTerm = {}
for k, v in pairs(term) do nativeTerm[k] = v end
local currentTerm = nativeTerm

function term.native() return nativeTerm end
function term.current() return currentTerm end
function term.redirect(target)
  if type(target) ~= "table" then error("bad argument #1 (expected table)", 2) end
  local old = currentTerm
  for k, native in pairs(nativeTerm) do
    term[k] = target[k] or native
  end
  currentTerm = target
  return old
end

-- ---------------------------------------------------------------- periféricos
function peripheral.wrap(name)
  if not peripheral.isPresent(name) then return nil end
  local wrapped = {}
  for _, method in ipairs(peripheral.getMethods(name)) do
    wrapped[method] = function(...) return peripheral.call(name, method, ...) end
  end
  return wrapped
end

function peripheral.find(ptype, filter)
  local found = {}
  for _, name in ipairs(peripheral.getNames()) do
    if peripheral.getType(name) == ptype then
      local w = peripheral.wrap(name)
      if not filter or filter(name, w) then found[#found + 1] = w end
    end
  end
  return unpack(found)
end

-- ---------------------------------------------------------------- http
local function httpWait(url)
  while true do
    local ev, u, a, b = os.pullEvent()
    if u == url then
      if ev == "http_success" then return a end
      if ev == "http_failure" then return nil, a, b end
    end
  end
end

function http.get(url, headers)
  local ok, err = http.request(url, nil, headers)
  if not ok then return nil, err end
  return httpWait(url)
end

function http.post(url, body, headers)
  local ok, err = http.request(url, body or "", headers)
  if not ok then return nil, err end
  return httpWait(url)
end

local rawPastebinPut = http.pastebinPut
function http.pastebinPut(name, content)
  local ok, err = rawPastebinPut(name, content)
  if not ok then return nil, err end
  return httpWait("https://pastebin.com/api/api_post.php")
end

-- ---------------------------------------------------------------- rednet (sobre modems sem fio)
rednet = {}
local BROADCAST = 65535
local openModems = {}

function rednet.open(side)
  if peripheral.getType(side) ~= "modem" then error("No such modem: " .. tostring(side), 2) end
  peripheral.call(side, "open", os.getComputerID())
  peripheral.call(side, "open", BROADCAST)
  openModems[side] = true
end

function rednet.close(side)
  local function closeOne(name)
    if peripheral.getType(name) == "modem" then
      peripheral.call(name, "close", os.getComputerID())
      peripheral.call(name, "close", BROADCAST)
    end
    openModems[name] = nil
  end
  if side then closeOne(side) else for name in pairs(openModems) do closeOne(name) end end
end

function rednet.isOpen(side)
  if side then return openModems[side] == true end
  return next(openModems) ~= nil
end

local function transmit(channel, message, protocol)
  local payload = { message = message, protocol = protocol }
  local sent = false
  for side in pairs(openModems) do
    peripheral.call(side, "transmit", channel, os.getComputerID(), payload)
    sent = true
  end
  return sent
end

function rednet.send(recipient, message, protocol)
  return transmit(recipient, message, protocol)
end

function rednet.broadcast(message, protocol)
  return transmit(BROADCAST, message, protocol)
end

function rednet.receive(protocolFilter, timeout)
  local timer = timeout and os.startTimer(timeout) or nil
  while true do
    local ev, a, channel, reply, payload = os.pullEvent()
    if ev == "modem_message" then
      if (channel == os.getComputerID() or channel == BROADCAST)
          and type(payload) == "table"
          and (protocolFilter == nil or payload.protocol == protocolFilter) then
        if timer then os.cancelTimer(timer) end
        return reply, payload.message, payload.protocol
      end
    elseif ev == "timer" and a == timer then
      return nil
    end
  end
end

-- ---------------------------------------------------------------- cores (combinar bits)
function colors.combine(...)
  local r = 0
  for i = 1, select("#", ...) do r = bit32.bor(r, (select(i, ...))) end
  return r
end
function colors.subtract(c, ...)
  for i = 1, select("#", ...) do c = bit32.band(c, bit32.bnot((select(i, ...)))) end
  return c
end
function colors.test(set, color) return bit32.band(set, color) == color end

-- ---------------------------------------------------------------- parallel
parallel = {}

local function runTasks(fns, anyMode)
  local tasks = {}
  for i, f in ipairs(fns) do
    if type(f) ~= "function" then error("bad argument #" .. i .. " (expected function)", 3) end
    tasks[i] = { co = coroutine.create(f), filter = nil, alive = true }
  end
  local alive = #tasks
  local ev = { n = 0 }
  while alive > 0 do
    for i, t in ipairs(tasks) do
      if t.alive and (t.filter == nil or t.filter == ev[1] or ev[1] == "terminate") then
        local ok, res = coroutine.resume(t.co, unpack(ev, 1, ev.n))
        if not ok then error(res, 0) end
        if coroutine.status(t.co) == "dead" then
          t.alive = false
          alive = alive - 1
          if anyMode then return i end
        else
          t.filter = res
        end
      end
    end
    if alive > 0 then ev = pack(os.pullEventRaw()) end
  end
end

function parallel.waitForAny(...) return runTasks({ ... }, true) end
function parallel.waitForAll(...) runTasks({ ... }, false) end

-- ---------------------------------------------------------------- textutils
textutils = {}

local function serializeValue(v, seen)
  local tv = type(v)
  if tv == "table" then
    if seen[v] then error("Cannot serialize table with recursive entries", 0) end
    seen[v] = true
    local parts, n = {}, #v
    for i = 1, n do parts[#parts + 1] = serializeValue(v[i], seen) end
    for k, val in pairs(v) do
      if not (type(k) == "number" and k >= 1 and k <= n and k % 1 == 0) then
        parts[#parts + 1] = "[" .. serializeValue(k, seen) .. "]=" .. serializeValue(val, seen)
      end
    end
    seen[v] = nil
    return "{" .. table.concat(parts, ",") .. "}"
  elseif tv == "string" then
    return string.format("%q", v)
  elseif tv == "number" then
    if v ~= v then return "0/0" end
    if v == math.huge then return "math.huge" end
    if v == -math.huge then return "-math.huge" end
    return tostring(v)
  elseif tv == "boolean" or tv == "nil" then
    return tostring(v)
  end
  error("Cannot serialize type " .. tv, 0)
end

function textutils.serialize(t) return serializeValue(t, {}) end

function textutils.unserialize(s)
  local f = load("return " .. tostring(s), "=unserialize", "t", { math = { huge = math.huge } })
  if not f then return nil end
  local ok, res = pcall(f)
  if ok then return res end
  return nil
end

local jsonEscapes = { ['"'] = '\\"', ["\\"] = "\\\\", ["\b"] = "\\b", ["\f"] = "\\f", ["\n"] = "\\n", ["\r"] = "\\r", ["\t"] = "\\t" }

local function jsonEncode(v, seen)
  local tv = type(v)
  if tv == "nil" then return "null"
  elseif tv == "boolean" then return tostring(v)
  elseif tv == "number" then
    if v ~= v or v == math.huge or v == -math.huge then error("Cannot serialize number: not finite", 0) end
    return tostring(v)
  elseif tv == "string" then
    return '"' .. v:gsub('[%c"\\]', function(c)
      return jsonEscapes[c] or string.format("\\u%04x", c:byte())
    end) .. '"'
  elseif tv == "table" then
    if seen[v] then error("Cannot serialize table with recursive entries", 0) end
    seen[v] = true
    local n, count = #v, 0
    for _ in pairs(v) do count = count + 1 end
    local parts = {}
    local result
    if count == n then
      for i = 1, n do parts[i] = jsonEncode(v[i], seen) end
      result = "[" .. table.concat(parts, ",") .. "]"
    else
      for k, val in pairs(v) do
        if type(k) ~= "string" and type(k) ~= "number" then error("Cannot serialize key of type " .. type(k), 0) end
        parts[#parts + 1] = jsonEncode(tostring(k), seen) .. ":" .. jsonEncode(val, seen)
      end
      result = "{" .. table.concat(parts, ",") .. "}"
    end
    seen[v] = nil
    return result
  end
  error("Cannot serialize type " .. tv, 0)
end

function textutils.serializeJSON(v) return jsonEncode(v, {}) end
textutils.serialiseJSON = textutils.serializeJSON

local function utf8char(cp)
  if cp < 0x80 then return string.char(cp)
  elseif cp < 0x800 then return string.char(0xC0 + math.floor(cp / 64), 0x80 + cp % 64)
  else return string.char(0xE0 + math.floor(cp / 4096), 0x80 + math.floor(cp / 64) % 64, 0x80 + cp % 64) end
end

function textutils.unserializeJSON(str)
  local pos = 1
  local function skip() pos = str:find("[^ \t\r\n]", pos) or (#str + 1) end
  local parseValue

  local function parseString()
    local out = {}
    pos = pos + 1
    while true do
      local c = str:sub(pos, pos)
      if c == "" then error("unterminated string", 0) end
      if c == '"' then pos = pos + 1 break end
      if c == "\\" then
        local e = str:sub(pos + 1, pos + 1)
        local map = { b = "\b", f = "\f", n = "\n", r = "\r", t = "\t", ['"'] = '"', ["\\"] = "\\", ["/"] = "/" }
        if e == "u" then
          out[#out + 1] = utf8char(tonumber(str:sub(pos + 2, pos + 5), 16) or 63)
          pos = pos + 6
        elseif map[e] then
          out[#out + 1] = map[e]
          pos = pos + 2
        else
          error("bad escape", 0)
        end
      else
        out[#out + 1] = c
        pos = pos + 1
      end
    end
    return table.concat(out)
  end

  function parseValue()
    skip()
    local c = str:sub(pos, pos)
    if c == "{" then
      local obj = {}
      pos = pos + 1
      skip()
      if str:sub(pos, pos) == "}" then pos = pos + 1 return obj end
      while true do
        skip()
        if str:sub(pos, pos) ~= '"' then error("expected string key", 0) end
        local key = parseString()
        skip()
        if str:sub(pos, pos) ~= ":" then error("expected ':'", 0) end
        pos = pos + 1
        obj[key] = parseValue()
        skip()
        local d = str:sub(pos, pos)
        pos = pos + 1
        if d == "}" then return obj end
        if d ~= "," then error("expected ',' or '}'", 0) end
      end
    elseif c == "[" then
      local arr, n = {}, 0
      pos = pos + 1
      skip()
      if str:sub(pos, pos) == "]" then pos = pos + 1 return arr end
      while true do
        n = n + 1
        arr[n] = parseValue()
        skip()
        local d = str:sub(pos, pos)
        pos = pos + 1
        if d == "]" then return arr end
        if d ~= "," then error("expected ',' or ']'", 0) end
      end
    elseif c == '"' then
      return parseString()
    elseif str:sub(pos, pos + 3) == "true" then pos = pos + 4 return true
    elseif str:sub(pos, pos + 4) == "false" then pos = pos + 5 return false
    elseif str:sub(pos, pos + 3) == "null" then pos = pos + 4 return nil
    else
      local num = str:match("^-?%d+%.?%d*[eE]?[+-]?%d*", pos)
      if not num or num == "" then error("unexpected character at " .. pos, 0) end
      pos = pos + #num
      return tonumber(num)
    end
  end

  local ok, res = pcall(parseValue)
  if ok then return res end
  return nil, res
end
textutils.unserialiseJSON = textutils.unserializeJSON

function textutils.formatTime(t, twentyFour)
  local h = math.floor(t)
  local m = math.floor((t - h) * 60)
  if twentyFour then return string.format("%d:%02d", h, m) end
  local suffix = h >= 12 and "PM" or "AM"
  local hh = h % 12
  if hh == 0 then hh = 12 end
  return string.format("%d:%02d %s", hh, m, suffix)
end

function textutils.urlEncode(str)
  return (tostring(str):gsub("[^%w%-%._~]", function(c) return string.format("%%%02X", c:byte()) end))
end

-- ---------------------------------------------------------------- require
local loadedModules = {}
function require(name)
  if loadedModules[name] ~= nil then return loadedModules[name] end
  local path = (name:gsub("%.", "/"))
  for _, pattern in ipairs({ "?.lua", "?/init.lua", "modules/?.lua" }) do
    local file = (pattern:gsub("%?", path))
    if fs.exists(file) and not fs.isDir(file) then
      local h = fs.open(file, "r")
      local src = h.readAll()
      h.close()
      local fn, err = load(src, "=" .. file, "t", _G)
      if not fn then error(err, 2) end
      local res = fn(name)
      if res == nil then res = true end
      loadedModules[name] = res
      return res
    end
  end
  error("module '" .. name .. "' not found", 2)
end

-- ---------------------------------------------------------------- cores <-> blit
local hexOf = {}
for i = 0, 15 do hexOf[2 ^ i] = string.format("%x", i) end
function colors.toBlit(color) return hexOf[color] or error("Invalid color", 2) end
function colors.fromBlit(ch)
  local n = tonumber(ch, 16)
  if not n or n < 0 or n > 15 then return nil end
  return 2 ^ n
end

-- ---------------------------------------------------------------- fs.find
function fs.find(pattern)
  local segs = {}
  for seg in pattern:gmatch("[^/]+") do segs[#segs + 1] = seg end
  local results = {}
  local function walk(dir, i)
    if i > #segs then results[#results + 1] = dir return end
    local seg = segs[i]
    if not seg:find("*", 1, true) then
      local path = fs.combine(dir, seg)
      if fs.exists(path) then walk(path, i + 1) end
      return
    end
    if not fs.isDir(dir) then return end
    local lp = "^" .. seg:gsub("[%^%$%(%)%%%.%[%]%+%-%?]", "%%%0"):gsub("%*", ".*") .. "$"
    for _, name in ipairs(fs.list(dir)) do
      if name:match(lp) then walk(fs.combine(dir, name), i + 1) end
    end
  end
  walk("", 1)
  table.sort(results)
  return results
end

-- ---------------------------------------------------------------- paintutils
paintutils = {}

function paintutils.drawPixel(x, y, color)
  if color then term.setBackgroundColor(color) end
  term.setCursorPos(x, y)
  term.write(" ")
end

function paintutils.drawLine(x1, y1, x2, y2, color)
  x1, y1, x2, y2 = math.floor(x1), math.floor(y1), math.floor(x2), math.floor(y2)
  local dx, dy = math.abs(x2 - x1), math.abs(y2 - y1)
  local sx = x1 < x2 and 1 or -1
  local sy = y1 < y2 and 1 or -1
  local err = dx - dy
  while true do
    paintutils.drawPixel(x1, y1, color)
    if x1 == x2 and y1 == y2 then break end
    local e2 = 2 * err
    if e2 > -dy then err = err - dy; x1 = x1 + sx end
    if e2 < dx then err = err + dx; y1 = y1 + sy end
  end
end

function paintutils.drawBox(x1, y1, x2, y2, color)
  paintutils.drawLine(x1, y1, x2, y1, color)
  paintutils.drawLine(x1, y2, x2, y2, color)
  paintutils.drawLine(x1, y1, x1, y2, color)
  paintutils.drawLine(x2, y1, x2, y2, color)
end

function paintutils.drawFilledBox(x1, y1, x2, y2, color)
  if y1 > y2 then y1, y2 = y2, y1 end
  for y = y1, y2 do paintutils.drawLine(x1, y, x2, y, color) end
end

function paintutils.parseImage(str)
  local image = {}
  for line in (str .. "\n"):gmatch("(.-)\n") do
    local row = {}
    for i = 1, #line do
      row[i] = colors.fromBlit(line:sub(i, i)) or 0
    end
    image[#image + 1] = row
  end
  return image
end

function paintutils.loadImage(path)
  local h = fs.open(path, "r")
  if not h then return nil end
  local data = h.readAll()
  h.close()
  return paintutils.parseImage(data)
end

function paintutils.drawImage(image, x, y)
  for j, row in ipairs(image) do
    for i = 1, #row do
      if row[i] ~= 0 then paintutils.drawPixel(x + i - 1, y + j - 1, row[i]) end
    end
  end
end

-- ---------------------------------------------------------------- window
window = {}

function window.create(parent, nX, nY, nWidth, nHeight, startVisible)
  if startVisible == nil then startVisible = true end
  local w, h = nWidth, nHeight
  local cx, cy = 1, 1
  local fg, bg = colors.white, colors.black
  local blink = false
  local visible = startVisible
  local lines = {}

  local function blankLine()
    local l = { t = {}, f = {}, b = {} }
    for i = 1, w do l.t[i] = " "; l.f[i] = fg; l.b[i] = bg end
    return l
  end
  for y = 1, h do lines[y] = blankLine() end

  local win = {}

  local function drawLine(y)
    if not visible then return end
    local l = lines[y]
    local t, f, b = {}, {}, {}
    for i = 1, w do
      t[i] = l.t[i]
      f[i] = hexOf[l.f[i]]
      b[i] = hexOf[l.b[i]]
    end
    parent.setCursorPos(nX, nY + y - 1)
    parent.blit(table.concat(t), table.concat(f), table.concat(b))
  end

  local function restore()
    if visible then
      parent.setCursorPos(nX + cx - 1, nY + cy - 1)
      parent.setCursorBlink(blink)
    end
  end

  function win.write(text)
    text = tostring(text)
    if cy >= 1 and cy <= h then
      local l = lines[cy]
      for i = 1, #text do
        local x = cx + i - 1
        if x >= 1 and x <= w then
          l.t[x] = text:sub(i, i); l.f[x] = fg; l.b[x] = bg
        end
      end
      drawLine(cy)
    end
    cx = cx + #text
    restore()
  end

  function win.blit(text, fgs, bgs)
    if cy >= 1 and cy <= h then
      local l = lines[cy]
      for i = 1, #text do
        local x = cx + i - 1
        if x >= 1 and x <= w then
          l.t[x] = text:sub(i, i)
          l.f[x] = colors.fromBlit(fgs:sub(i, i)) or colors.white
          l.b[x] = colors.fromBlit(bgs:sub(i, i)) or colors.black
        end
      end
      drawLine(cy)
    end
    cx = cx + #text
    restore()
  end

  function win.clear()
    for y = 1, h do lines[y] = blankLine(); drawLine(y) end
    restore()
  end
  function win.clearLine()
    if cy >= 1 and cy <= h then lines[cy] = blankLine(); drawLine(cy) end
    restore()
  end
  function win.scroll(n)
    if n == 0 then return end
    local new = {}
    for y = 1, h do new[y] = lines[y + n] or blankLine() end
    lines = new
    for y = 1, h do drawLine(y) end
    restore()
  end
  function win.setCursorPos(x, y) cx, cy = x, y; restore() end
  function win.getCursorPos() return cx, cy end
  function win.setCursorBlink(b) blink = b; restore() end
  function win.getCursorBlink() return blink end
  function win.getSize() return w, h end
  function win.isColor() return true end
  win.isColour = win.isColor
  function win.setTextColor(c) fg = c end
  function win.getTextColor() return fg end
  function win.setBackgroundColor(c) bg = c end
  function win.getBackgroundColor() return bg end
  win.setTextColour, win.getTextColour = win.setTextColor, win.getTextColor
  win.setBackgroundColour, win.getBackgroundColour = win.setBackgroundColor, win.getBackgroundColor

  function win.redraw()
    for y = 1, h do drawLine(y) end
    restore()
  end
  win.restoreCursor = restore
  function win.setVisible(v)
    visible = v and true or false
    if visible then win.redraw() end
  end
  function win.isVisible() return visible end
  function win.getPosition() return nX, nY end
  function win.reposition(x, y, newW, newH)
    nX, nY = x, y
    if newW and newH then
      local new = {}
      for j = 1, newH do new[j] = lines[j] or nil end
      w, h = newW, newH
      for j = 1, h do
        if not new[j] then new[j] = blankLine() end
        for i = #new[j].t + 1, w do new[j].t[i] = " "; new[j].f[i] = fg; new[j].b[i] = bg end
      end
      lines = new
    end
    if visible then win.redraw() end
  end

  if visible then win.redraw() end
  return win
end

-- ---------------------------------------------------------------- proteção contra strings gigantes
local rawRep = string.rep
function string.rep(str, n, sep)
  str = tostring(str)
  n = tonumber(n) or 0
  if n > 0 and (#str + #(sep or "")) * n > 4000000 then error("string too large", 2) end
  return rawRep(str, n, sep)
end

-- ---------------------------------------------------------------- settings
settings = {}
do
  local values, defs = {}, {}

  function settings.define(name, details) defs[name] = details or {} end
  function settings.undefine(name) defs[name] = nil end

  function settings.set(name, value)
    if type(name) ~= "string" then error("bad argument #1 (expected string)", 2) end
    local t = type(value)
    if t == "function" or t == "thread" or t == "userdata" then error("Cannot set a " .. t .. " setting", 2) end
    values[name] = value
  end

  function settings.get(name, default)
    local v = values[name]
    if v ~= nil then return v end
    if default ~= nil then return default end
    local d = defs[name]
    return d and d.default
  end

  function settings.getDetails(name)
    local d = defs[name] or {}
    return { description = d.description, default = d.default, type = d.type, value = values[name] }
  end

  function settings.unset(name) values[name] = nil end
  function settings.clear() values = {} end

  function settings.getNames()
    local seen, names = {}, {}
    for k in pairs(values) do if not seen[k] then seen[k] = true; names[#names + 1] = k end end
    for k in pairs(defs) do if not seen[k] then seen[k] = true; names[#names + 1] = k end end
    table.sort(names)
    return names
  end

  function settings.save(path)
    local h = fs.open(path or ".settings", "w")
    if not h then return false end
    h.write(textutils.serialize(values))
    h.close()
    return true
  end

  function settings.load(path)
    path = path or ".settings"
    if not fs.exists(path) or fs.isDir(path) then return false end
    local h = fs.open(path, "r")
    local data = textutils.unserialize(h.readAll())
    h.close()
    if type(data) ~= "table" then return false end
    for k, v in pairs(data) do
      if type(k) == "string" then values[k] = v end
    end
    return true
  end
end
pcall(settings.load)

-- ---------------------------------------------------------------- inicia o shell
term.setBackgroundColor(colors.black)
term.setTextColor(colors.white)
term.clear()
term.setCursorPos(1, 1)

local src = __sys.resource("shell.lua")
local fn, err = load(src, "=shell")
if not fn then
  printError("Erro na bios: " .. tostring(err))
  os.pullEventRaw("key")
  os.shutdown()
end

local ok, e = pcall(fn)
if not ok then
  printError("Erro no shell: " .. tostring(e))
  printError("Pressione uma tecla para desligar.")
  os.pullEventRaw("key")
end
os.shutdown()
