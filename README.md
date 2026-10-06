[![Watch the YouTube showcase](https://img.youtube.com/vi/IuH9Ax2z_fE/hqdefault.jpg)](https://youtu.be/IuH9Ax2z_fE)

# Entropy Inversion Protocol

**Entropy Inversion Protocol** is a Minecraft Forge 1.20.1 mod.

> This project was vibe-coded with the assistance of AI.

Strike a designated position with an advanced weapon from beyond the galaxy, erasing terrain and entities in the surrounding area.

> [!CAUTION]
> Strikes permanently destroy terrain. Back up your world before use.

## Quick Start

1. Obtain the Entropy Inversion Requestor from the **Tools & Utilities** tab in Creative mode.
2. Hold the item and right-click to enter targeting mode. Crouch and right-click to open the attack mode selector.
3. At a range of up to 1,024 blocks, briefly left-click a block or fluid, such as water, to select it. The selected block is highlighted with a cyan outline. Briefly left-click another position to change the target.
4. Set the strike radius, then hold left-click for one second to request the strike.
5. The selected attack begins after its countdown: 10 seconds for the standard and microbot modes, 13 seconds for Asteroid Guidance.

## Controls

| Input | Action |
| --- | --- |
| Right-click | Enter targeting mode |
| Crouch + right-click | Open attack mode selector; hover over a mode for its description |
| Brief left-click | Select or change the targeted block |
| Hold left-click for 1 second | Request a strike at the selected target |
| Mouse wheel | Adjust the radius in 5-block increments |
| Alt + mouse wheel | Adjust the radius in 1-block increments |
| Right-click or Esc | Cancel targeting |

The mouse wheel does not change hotbar slots while targeting. The initial radius is **10 blocks**, and the last selected radius is retained for the next targeting session. The radius can be set from 1 to 200 blocks. The maximum target range is 1,024 blocks. For distant targets, the server loads the relevant chunk and validates the line of sight and target block.

## Strike and Effects

- The strike area is shown with a world-space boundary and a circular HUD preview. The preview scales with the selected radius.
- The radius preview reflects the selected mode: vertical boundaries for full-height modes and a surface ring for Asteroid Guidance.
- Crouch-right-click the Requestor to choose one of three attacks. The selected mode is shown in the item tooltip and targeting HUD.
- **Entropy Inversion Protocol** removes blocks and entities throughout the selected full-height cylinder.
- **Asteroid Guidance** shows a meteor and selected crater radius in the world while aiming, even before a target is locked. Meteor diameter and the impact remnant scale with the selected radius (3 to 11 blocks across). Its terrain-following boundary also has elevated cyan and orange guide rings, perimeter posts up to 32 blocks high, and repeating flame/end-rod particle columns to remain visible above uneven ground. During its 13-second cutscene, the meteor descends in a smooth, eased arc from within client view distance. Impact clears blocks at and above the target's ground height within the selected radius, carves a bowl-shaped crater below, eliminates entities in the impact volume, and leaves a radius-scaled remnant of magma, blackstone, obsidian, and basalt. The exposed crater interior and the surrounding outer ring both retain a mixed, scorched-block surface.
- **Anti-organic Microbots** outlines every living entity that will be eliminated and repeatedly surrounds it with portal, end-rod, and witch particles during the final eight seconds of the full-screen cutscene. The marked targets are eliminated when the countdown ends. Blocks are untouched; Creative and Spectator players are excluded.
- Players inside the strike radius receive a warning during the countdown.
- After the selected mode's cutscene, the attack is applied at the target.
- Water inside the strike area is cleared again after block removal to eliminate fluid that flows back while chunks are being processed.
- An irregular scorched zone remains outside the strike area. Its width scales with the radius and includes a mix of magma blocks, blackstone, and basalt placed along the existing surface.
- The Requestor has a custom 3D radio-style model with an antenna, tuning dials, speaker grille, and illuminated display.
- Entities killed by the strike are credited to the player who requested it.

## Behavior and Limitations

- Players in Creative or Spectator mode are excluded from entity removal.
- The Requestor is not consumed and has a 10-second cooldown after a strike is requested.
- Target and radius-boundary particles appear while aiming. Sounds play when targeting starts, the radius changes, the cutscene transitions, and the strike hits.
- The Requestor cannot be crafted; it is available in Creative mode.

## Future Plans

- No additional attack modes are currently planned.

---

# 엔트로피 역전 프로토콜

**Entropy Inversion Protocol**은 Minecraft Forge 1.20.1용 모드입니다.

> 이 프로젝트는 AI의 도움을 받아 바이브 코딩으로 개발되었습니다.

은하 너머의 오버테크놀로지 무기로 지정한 좌표를 타격하고, 그 주변의 지형과 엔티티를 소멸시킵니다.

> [!CAUTION]
> 타격은 지형을 영구적으로 파괴합니다. 사용 전에 월드를 백업하세요.

## 빠른 시작

1. 크리에이티브 모드의 **도구 및 유용한 물건** 탭에서 엔트로피 역전 요청기를 획득합니다.
2. 아이템을 들고 우클릭해 조준 모드에 들어갑니다. 웅크린 채 우클릭하면 공격 모드 선택 화면이 열립니다.
3. 최대 1024블록 거리에서 바라보는 블록이나 물 같은 액체를 짧게 좌클릭해 목표를 지정합니다. 선택된 블록은 청록색 외곽선으로 강조되며, 다른 지점을 짧게 좌클릭하면 목표를 변경할 수 있습니다.
4. 타격 반경을 설정한 뒤 좌클릭을 1초 동안 길게 눌러 타격을 요청합니다.
5. 선택한 공격의 카운트다운 후 시작됩니다. 기본/마이크로봇 모드는 10초, 소행성 유도 모드는 13초입니다.

## 조작법

| 입력 | 동작 |
| --- | --- |
| 우클릭 | 조준 모드 진입 |
| 웅크린 채 우클릭 | 공격 모드 선택 화면 열기 · 모드 위에 커서를 올려 설명 확인 |
| 짧게 좌클릭 | 바라보는 블록을 목표로 지정하거나 변경 |
| 좌클릭 1초간 누르기 | 지정된 목표에 타격 요청 |
| 마우스 휠 | 반경을 5블록씩 조정 |
| Alt + 마우스 휠 | 반경을 1블록씩 정밀 조정 |
| 우클릭 또는 Esc | 조준 취소 |

조준 모드에서는 마우스 휠로 핫바 슬롯이 바뀌지 않습니다. 첫 조준의 기본 반경은 **10블록**이며, 반경을 변경하면 다음 조준에도 마지막 값이 유지됩니다. 반경은 1~200블록까지 설정할 수 있습니다. 목표 지정 최대 거리는 1024블록입니다. 먼 거리의 목표는 서버가 해당 청크를 불러온 뒤 시야 경로와 목표 블록을 검증합니다.

## 타격 및 연출

- 타격 범위는 월드의 경계선과 HUD 원형 미리보기로 표시됩니다. 미리보기 크기는 선택한 반경에 맞춰 조정됩니다.
- 반경 미리보기는 선택한 모드에 맞춰 표시됩니다. 월드 전체 높이 모드는 수직 경계를, 소행성 유도는 지표 원형 경계를 보여줍니다.
- 요청기를 웅크린 채 우클릭하면 세 가지 공격 모드를 선택할 수 있습니다. 선택 모드는 아이템 설명과 조준 HUD에 표시됩니다.
- **엔트로피 역전 프로토콜**은 선택 반경의 월드 전체 높이 원기둥 범위에서 블록과 엔티티를 소멸시킵니다.
- **소행성 유도**는 목표를 고정하기 전에도 조준 중인 지점 주위에 소행성과 분화구 범위를 월드에 표시합니다. 소행성과 충돌 후 잔해의 지름은 선택 반경에 따라 3~11블록으로 함께 커집니다. 지형을 따라가는 경계 외에도 지형 위 청록/주황색 안내 링과 최대 32블록 높이의 원주 표식, 반복되는 불꽃/엔드 막대 입자 기둥을 표시해 지형 기복에도 범위가 잘 보이게 했습니다. 13초 컷씬 도중 운석은 플레이어 시야 거리 안에서 부드러운 궤적으로 낙하하며 지면 가까이에서 감속합니다. 충돌 시 목표 지표 높이 이상에서 반경 내 블록을 모두 제거하고 아래쪽에 그릇 모양 분화구를 만듭니다. 분화구 안쪽 파인 표면과 바깥쪽 모두에 마그마 블록·흑암·현무암·흑요석이 섞인 그을린 흔적을 남기고, 분화구 바닥에는 반경에 비례해 크기가 조정된 소행성 잔해를 둡니다.
- **반유기체 마이크로봇 살포**는 전체 화면 컷씬 마지막 8초 동안 제거될 생명체의 윤곽을 발광 효과로 강조하고, 포탈/엔드 막대/마녀 입자를 반복 생성해 표시한 뒤 카운트다운이 끝나면 해당 대상을 제거합니다. 블록은 손상되지 않으며 크리에이티브 및 관전자 플레이어는 제외됩니다.
- 요청 후 카운트다운 중에는 타격 범위 안의 플레이어에게 경고가 표시됩니다.
- 컷씬 이후 선택한 공격이 목표에 적용됩니다.
- 청크별 파괴 처리 중 범위 안으로 다시 흘러든 물이 남지 않도록, 블록 파괴 후 타격 범위의 물을 한 번 더 정리합니다.
- 타격 범위의 바깥에는 반경에 비례해 폭이 넓고 가장자리가 불규칙하게 튀어나온 그을린 지대가 남습니다. 마그마 블록, 흑암과 현무암이 기존 지표를 따라 섞여 생성됩니다.
- 타격으로 사망한 생명체는 요청 플레이어의 공격으로 처리됩니다.
- 요청기는 안테나, 조절 다이얼, 스피커 그릴과 발광 화면이 있는 무전기 형태의 3D 모델을 사용합니다.

## 동작 및 제한

- 크리에이티브 및 관전 모드 플레이어는 엔티티 제거 대상에서 제외됩니다.
- 요청기는 소모되지 않으며, 타격 요청 후 10초의 재사용 대기시간이 있습니다.
- 조준 시 목표 지점과 반경 경계에 입자 효과가 표시됩니다. 조준 시작, 반경 변경, 컷씬 전환 및 타격 시 사운드가 재생됩니다.
- 요청기는 제작할 수 없으며 크리에이티브 모드에서 획득할 수 있습니다.

## 향후 계획

- 현재 예정된 추가 공격 모드는 없습니다.