# Orbital Strike

Minecraft Forge 1.20.1 mod. The Orbital Strike Requestor is available in the
Creative Tools & Utilities tab and has no crafting recipe.

Right-click to enter targeting mode. A 200-block-radius vertical cylinder is
shown around the aimed block. Hold left-click for one second to request the
strike; right-click or Escape cancels targeting. After a five-second
countdown/cutscene, the server removes vulnerable entities in the cylinder and
clears blocks from the dimension's minimum to maximum build height. Creative and
spectator players are excluded from entity removal. The requestor is not
consumed and has a ten-second cooldown. The terrain change is destructive and
permanent; back up worlds before use.

The target boundary is drawn in-world and as a HUD radius preview.

Block clearing is processed in small chunk batches to limit single-tick stalls.
The strike loads affected chunks as it processes them.
