-- Mostra a imagem da câmera no monitor (ou no próprio terminal).
local cam = assert(peripheral.find("camera"), "Nenhuma câmera encontrada")
local mon = peripheral.find("monitor")
if mon then
  mon.setTextScale(0.5)
  term.redirect(mon)
end

while true do
  local w, h = term.getSize()
  local ok, img = pcall(cam.capture, math.min(w, 32), math.min(h, 24))
  if ok then
    for y, row in ipairs(img) do
      for x, color in ipairs(row) do
        term.setCursorPos(x, y)
        term.setBackgroundColor(color)
        term.write(" ")
      end
    end
  end
  sleep(0.5)
end
