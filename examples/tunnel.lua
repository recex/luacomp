-- Turtle: cava um túnel de N blocos para frente.  Uso: tunnel 10
local n = tonumber(({ ... })[1]) or 5
for i = 1, n do
  while turtle.detect() do turtle.dig() end   -- repete por causa de areia/cascalho
  local ok, err = turtle.forward()
  if not ok then print("Parou: " .. tostring(err)) return end
  turtle.digUp()
end
print("Túnel pronto!")
