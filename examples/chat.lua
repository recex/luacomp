-- Chat simples entre computadores com modem sem fio.  Uso: chat <lado do modem>
local side = ({ ... })[1] or "top"
rednet.open(side)
print("Chat aberto. Digite e dê Enter.")

local function receiver()
  while true do
    local id, msg = rednet.receive("chat")
    print("[" .. id .. "] " .. tostring(msg))
  end
end

local function sender()
  while true do
    local line = read()
    if line ~= "" then rednet.broadcast(line, "chat") end
  end
end

parallel.waitForAny(receiver, sender)
