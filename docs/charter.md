# Charter — LabFlow AI demo (SWT301)

> **Dự án demo do AI dựng cho môn SWT301, KHÔNG phải đồ án LabFlow; không chép sang đồ án.**
> Đồ án thật (LabFlow AI, project `LF` trên CT Work) do nhóm/người dùng tự code tay 100%.

## Mục tiêu
- Có một hệ thống Spring Boot **thật, chạy được, code sạch** làm đối tượng kiểm thử cho SWT301:
  Lab 2 unit test (Report 5.1) → Lab 3 integration test (5.2) → Lab 4/5 system test (5.3).
- Đi **đúng kế hoạch đồ án LabFlow AI v2** (cùng mã Req S01…S47/N1…N6, BR-01…14, gate, vai C1–C5, stack, kiến trúc)
  để nhóm **quan sát** cách một nhóm thật lập kế hoạch, phân công, code, review, báo cáo trên CT Work.

## Phạm vi bản v0.1-lab2 (Sprint 1, 09–11/10/2026)
Backend tuần 1–4 của kế hoạch, dồn lại (CR-1): nền tảng, identity + RBAC, campus/building/lab, thiết bị,
lịch mở cửa + blackout, availability, spike chống đặt trùng (G0).

## Ngoài phạm vi (có Change Request)
- Frontend React + wireframe (CR-2) — làm sau Lab 2.
- v2 SEP490: IoT, RAG, vision, safety (CR-3).

## Vai (vertical slice, theo kế hoạch)
| Vai | Ai | Miền |
|---|---|---|
| C1 Leader | Cuong Hoang (trưởng nhóm) | Identity & tạo booking, kiến trúc, review, merge, release |
| C2 | AI agent "Cường 1" | Lab, lịch mở cửa, availability, approval, báo cáo; SRS §1, DB script + seed |
| C3 | AI agent "Cường 2" | Register/quên mật khẩu, huỷ/đổi, waitlist, check-in; System Test |
| C4 | AI agent "Cường 3" | Thiết bị, mượn/trả; Project Tracking, Weekly Report, deploy |
| C5 | AI agent "Cường 4" | User/role/settings, bảo trì, audit |

## Luật trung thực
Không giả commit, không giả approval, không giả teamwork: mọi thẻ ghi rõ ai (người hay agent) làm gì.

## Definition of Done (kế hoạch §DoD + LABFLOW_MAPPING §7)
- [ ] Acceptance criteria đạt, demo được bằng dữ liệu tái lập
- [ ] Backend có authorization, validation, audit và error path
- [ ] Migration chạy được trên database sạch
- [ ] Test phù hợp rủi ro — không chỉ happy path
- [ ] CI xanh, không lộ secret, không còn lỗi P0/P1
- [ ] Evidence: commit / test report điền vào thẻ
- [ ] Mọi input validate: bắt buộc, độ dài, định dạng — cả backend (Bean Validation + service)
- [ ] Mọi danh sách: search + filter + sort + paging ở server
- [ ] Đã merge main trong ngày; tài liệu cập nhật
