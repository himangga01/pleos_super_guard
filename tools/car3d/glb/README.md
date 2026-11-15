# 3D 아반떼 GLB 보관

앱이 쓰는 모델은 `demo-app/app/src/main/assets/models/avante.glb`입니다. 이 폴더는 판별 사본을 로컬에 보관합니다(사용자 요청 2026-11-15 "glb는 저장해 로컬에").

| 파일 | 바이트 | SHA-256 | 삼각형(그려지는 수) | 노드 | 재질 | GLB 속 그림 |
| --- | ---: | --- | ---: | ---: | ---: | --- |
| `avante_pass5_2026-11-14.glb` | 2,098,788 | `1db254bc5039e8074f4576de7916131e2622ff17ea6a9135e04a7477840606fb` | 109,060 | 75 | 32 | ShadowTex |
| `avante_pass6_front_2026-11-15.glb` | 2,115,212 | `2356520421b28f4d001c496171b598ae3fff78519160ee14b8af6aa866bf3ed8` | 109,398 | 80 | 33 | ShadowTex |

- `avante_pass6_front_2026-11-15.glb`: 지금 앱에 들어 있는 판(앞모습 6회, Fable 5.2). 앱 asset · APK · 에뮬레이터 설치본과 해시가 같습니다(2026-11-15 확인).
- `avante_pass5_2026-11-14.glb`: 바로 앞 판(5회). 되돌릴 때 `assets/models/avante.glb`에 복사하고 미리 그린 그림(`car3d_home.webp`, `car3d_interior.webp`)도 5회 판으로 바꿔야 합니다. 5회 그림은 `avante_model.pass5.py`로 다시 만들 수 있습니다.
- 삼각형 수는 화면에 그려지는 수입니다. 네 바퀴가 같은 메시를 함께 쓰므로 파일 안 고유 메시만 세면 93,709(5회) · 94,047(6회)입니다.
- 만드는 법: `avante_model.py`(Blender 4.5 LTS). 사진 텍스처는 없고, GLB 속 그림은 바닥 그림자(Shadow) 하나뿐입니다.
- 자세한 기록: `docs/analysis/car-3d-and-icon-ui-2026-11-14.md` 2.2절.
