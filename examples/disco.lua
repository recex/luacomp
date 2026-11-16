-- Copia o programa para o disquete e mostra os arquivos dele.
local drive = peripheral.find("drive")
if not drive or not drive.isDiskPresent() then print("Coloque um disquete no leitor.") return end
local mount = drive.getMountPath()
fs.copy("startup.lua", mount .. "/startup.lua")
print("Arquivos em " .. mount .. ":")
for _, name in ipairs(fs.list(mount)) do print(" " .. name) end
