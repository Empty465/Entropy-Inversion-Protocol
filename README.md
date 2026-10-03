# Orbital Strike

Minecraft Forge 1.20.1 mod. The Orbital Strike Requestor is available in the
Creative Tools & Utilities tab and has no crafting recipe.

Aim at a block within 512 blocks and right-click. After a five-second
countdown/cutscene, the server removes entities in a 200-block-radius horizontal
cylinder and clears blocks from the dimension's minimum to maximum build height.
The requestor is not consumed and has a ten-second cooldown. The terrain change
is destructive and permanent; back up worlds before use.

Block clearing is processed in small chunk batches to limit single-tick stalls.
The strike loads affected chunks as it processes them.
