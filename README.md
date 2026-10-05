[![Watch the YouTube showcase](https://img.youtube.com/vi/IuH9Ax2z_fE/hqdefault.jpg)](https://youtu.be/IuH9Ax2z_fE)

# Entropy Inversion Protocol

**Entropy Inversion Protocol** is a Minecraft Forge 1.20.1 mod.

> This project was vibe-coded with the assistance of AI.

Strike a designated position with an advanced weapon from beyond the galaxy, erasing terrain and entities in the surrounding area.

> [!CAUTION]
> Strikes permanently destroy terrain. Back up your world before use.

## Quick Start

1. Obtain the Entropy Inversion Requestor from the **Tools & Utilities** tab in Creative mode.
2. Hold the item and right-click to enter targeting mode.
3. At a range of up to 1,024 blocks, briefly left-click a block or fluid, such as water, to select it. The selected block is highlighted with a cyan outline. Briefly left-click another position to change the target.
4. Set the strike radius, then hold left-click for one second to request the strike.
5. The strike hits the selected position after a 10-second countdown.

## Controls

| Input | Action |
| --- | --- |
| Right-click | Enter targeting mode |
| Brief left-click | Select or change the targeted block |
| Hold left-click for 1 second | Request a strike at the selected target |
| Mouse wheel | Adjust the radius in 5-block increments |
| Alt + mouse wheel | Adjust the radius in 1-block increments |
| Right-click or Esc | Cancel targeting |

The mouse wheel does not change hotbar slots while targeting. The initial radius is **10 blocks**, and the last selected radius is retained for the next targeting session. The radius can be set from 1 to 200 blocks. The maximum target range is 1,024 blocks. For distant targets, the server loads the relevant chunk and validates the line of sight and target block.

## Strike and Effects

- The strike area is shown with a world-space boundary and a circular HUD preview. The preview scales with the selected radius.
- The strike affects a vertical cylindrical area centered on the designated position.
- Players inside the strike radius receive a warning during the countdown.
- After a 10-second cutscene, blocks and living entities are erased within a cylindrical column centered on the target.
- Water inside the strike area is cleared again after block removal to eliminate fluid that flows back while chunks are being processed.
- An irregular scorched zone remains outside the strike area. Its width scales with the radius and includes a mix of magma blocks, blackstone, and basalt placed along the existing surface.
- Entities killed by the strike are credited to the player who requested it.

## Behavior and Limitations

- Players in Creative or Spectator mode are excluded from entity removal.
- The Requestor is not consumed and has a 10-second cooldown after a strike is requested.
- Target and radius-boundary particles appear while aiming. Sounds play when targeting starts, the radius changes, the cutscene transitions, and the strike hits.
- The Requestor cannot be crafted; it is available in Creative mode.

## Future Plans

- Add other attack types for the Requestor, such as instantly eliminating entities within range without destroying blocks, or guiding nearby asteroids to bombard the area.

---

# 엔트로피 역전 프로토콜

**Entropy Inversion Protocol**은 Minecraft Forge 1.20.1용 모드입니다.

> 이 프로젝트는 AI의 도움을 받아 바이브 코딩으로 개발되었습니다.

은하 너머의 오버테크놀로지 무기로 지정한 좌표를 타격하고, 그 주변의 지형과 엔티티를 소멸시킵니다.

> [!CAUTION]
> 타격은 지형을 영구적으로 파괴합니다. 사용 전에 월드를 백업하세요.

## 빠른 시작

1. 크리에이티브 모드의 **도구 및 유용한 물건** 탭에서 엔트로피 역전 요청기를 획득합니다.
2. 아이템을 들고 우클릭해 조준 모드에 들어갑니다.
3. 최대 1024블록 거리에서 바라보는 블록이나 물 같은 액체를 짧게 좌클릭해 목표를 지정합니다. 선택된 블록은 청록색 외곽선으로 강조되며, 다른 지점을 짧게 좌클릭하면 목표를 변경할 수 있습니다.
4. 타격 반경을 설정한 뒤 좌클릭을 1초 동안 길게 눌러 타격을 요청합니다.
5. 10초 카운트다운이 끝나면 지정 위치에 타격이 발생합니다.

## 조작법

| 입력 | 동작 |
| --- | --- |
| 우클릭 | 조준 모드 진입 |
| 짧게 좌클릭 | 바라보는 블록을 목표로 지정하거나 변경 |
| 좌클릭 1초간 누르기 | 지정된 목표에 타격 요청 |
| 마우스 휠 | 반경을 5블록씩 조정 |
| Alt + 마우스 휠 | 반경을 1블록씩 정밀 조정 |
| 우클릭 또는 Esc | 조준 취소 |

조준 모드에서는 마우스 휠로 핫바 슬롯이 바뀌지 않습니다. 첫 조준의 기본 반경은 **10블록**이며, 반경을 변경하면 다음 조준에도 마지막 값이 유지됩니다. 반경은 1~200블록까지 설정할 수 있습니다. 목표 지정 최대 거리는 1024블록입니다. 먼 거리의 목표는 서버가 해당 청크를 불러온 뒤 시야 경로와 목표 블록을 검증합니다.

## 타격 및 연출

- 타격 범위는 월드의 경계선과 HUD 원형 미리보기로 표시됩니다. 미리보기 크기는 선택한 반경에 맞춰 조정됩니다.
- 타격은 지정 좌표를 기준으로 수직 원기둥 범위에 적용됩니다.
- 요청 후 카운트다운 중에는 타격 범위 안의 플레이어에게 경고가 표시됩니다.
- 10초 컷씬 이후 목표를 중심으로한 원형 기둥으로 블록과 생명체를 소멸시킵니다.
- 청크별 파괴 처리 중 범위 안으로 다시 흘러든 물이 남지 않도록, 블록 파괴 후 타격 범위의 물을 한 번 더 정리합니다.
- 타격 범위의 바깥에는 반경에 비례해 폭이 넓고 가장자리가 불규칙하게 튀어나온 그을린 지대가 남습니다. 마그마 블록, 흑암과 현무암이 기존 지표를 따라 섞여 생성됩니다.
- 타격으로 사망한 생명체는 요청 플레이어의 공격으로 처리됩니다.

## 동작 및 제한

- 크리에이티브 및 관전 모드 플레이어는 엔티티 제거 대상에서 제외됩니다.
- 요청기는 소모되지 않으며, 타격 요청 후 10초의 재사용 대기시간이 있습니다.
- 조준 시 목표 지점과 반경 경계에 입자 효과가 표시됩니다. 조준 시작, 반경 변경, 컷씬 전환 및 타격 시 사운드가 재생됩니다.
- 요청기는 제작할 수 없으며 크리에이티브 모드에서 획득할 수 있습니다.

## 향후 계획

- 요청기 텍스쳐 개선 또는 3D 모델링 적용
- 요청기로 다른 형태의 공격 추가 (블록 파괴없이 범위 내 생명체만 즉사, 주변 소행성을 유도하여 폭격 등)