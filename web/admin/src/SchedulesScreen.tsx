import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { api, ApiError, type Schedule, type User, type Vehicle } from './api'
import { ErrorBox } from './ErrorBox'
import { addDays, dayLabel, kstDate, kstIso, kstTime, mondayOf, todayKst } from './time'

// 시각은 30분 단위로 고른다 — 브라우저 시간 칸(오전/오후·시·분 따로)은 맞추기 번거롭다(2026-11-15)
const TIMES = Array.from({ length: 48 }, (_, i) => `${String(Math.floor(i / 2)).padStart(2, '0')}:${i % 2 ? '30' : '00'}`)
const PRESETS = [
  { label: '오전', start: '09:00', end: '12:00' },
  { label: '오후', start: '13:00', end: '18:00' },
  { label: '하루 종일', start: '09:00', end: '18:00' },
] as const

/** 'HH:mm' + 분. 하루를 넘기면 23:30에서 멈춘다. */
const plus = (t: string, minutes: number) => {
  const i = Math.min(TIMES.indexOf(t) + minutes / 30, TIMES.length - 1)
  return TIMES[i]
}
const timeLabel = (t: string) => {
  const h = Number(t.slice(0, 2))
  return `${h < 12 ? '오전' : '오후'} ${h % 12 === 0 ? 12 : h % 12}:${t.slice(3)}`
}

/**
 * 배차 (F-53). 주간 표(차량 행 × 요일 열)로 보고, 차량·운전자·시간을 등록한다.
 * 등록하면 그 차로만 배포된다(F-43). 차는 이 배차로 운전자를 맨 앞에 놓고 추론한다(±1시간).
 * 목적지·목적은 받지 않는다(2026-11-15 — 목적은 [목적] 탭의 출발→도착 규칙이 정한다).
 */
export function SchedulesScreen() {
  const [week, setWeek] = useState(() => mondayOf(todayKst()))
  const [vehicles, setVehicles] = useState<Vehicle[]>([])
  const [users, setUsers] = useState<User[]>([])
  const [schedules, setSchedules] = useState<Schedule[]>([])
  const [error, setError] = useState<ApiError | null>(null)
  const [notice, setNotice] = useState('')
  const [busy, setBusy] = useState(false)
  const [form, setForm] = useState({
    vehicleId: '',
    userId: '',
    date: todayKst(),
    start: '09:00',
    end: '12:00',
  })

  const days = useMemo(() => Array.from({ length: 7 }, (_, i) => addDays(week, i)), [week])

  const loadWeek = () =>
    api
      .schedules(kstIso(week), kstIso(addDays(week, 7)))
      .then(setSchedules)
      .catch(e => setError(e as ApiError))

  useEffect(() => {
    Promise.all([api.vehicles(), api.users()])
      .then(([v, u]) => {
        setVehicles(v)
        setUsers(u)
        setForm(f => ({ ...f, vehicleId: f.vehicleId || v[0]?.vehicleId || '' }))
      })
      .catch(e => setError(e as ApiError))
  }, [])

  useEffect(() => {
    loadWeek()
  }, [week])

  const set = (k: keyof typeof form) => (e: { target: { value: string } }) => {
    const value = e.target.value
    setForm(f => {
      const next = { ...f, [k]: value }
      // 시작을 옮기면 끝도 같은 길이만큼 따라간다(끝이 시작보다 앞서지 않게)
      if (k === 'start') {
        const length = Math.max(TIMES.indexOf(f.end) - TIMES.indexOf(f.start), 1) * 30
        next.end = plus(value, length)
      }
      return next
    })
  }

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    setNotice('')
    if (!form.vehicleId) return setError(new ApiError(400, 'FORM', '차량을 먼저 등록해 주세요'))
    if (form.end <= form.start) return setError(new ApiError(400, 'FORM', '끝나는 시각이 시작보다 늦어야 해요'))
    setBusy(true)
    try {
      await api.createSchedule({
        vehicleId: form.vehicleId,
        userId: form.userId || undefined,
        startsAt: kstIso(form.date, form.start),
        endsAt: kstIso(form.date, form.end),
      })
      setNotice('배차를 등록했어요. 그 차로 보냈어요.')
      const w = mondayOf(form.date)
      if (w !== week) setWeek(w)
      else await loadWeek()
    } catch (err) {
      setError(err as ApiError)
    } finally {
      setBusy(false)
    }
  }

  const remove = async (s: Schedule) => {
    if (!confirm(`${describe(s)}\n이 배차를 지울까요?`)) return
    setError(null)
    try {
      await api.deleteSchedule(s.scheduleId)
      setNotice('배차를 지웠어요.')
      await loadWeek()
    } catch (err) {
      setError(err as ApiError)
    }
  }

  const userName = (id: string | null) => (id ? (users.find(u => u.userId === id)?.name ?? '?') : '운전자 미정')
  const describe = (s: Schedule) =>
    `${kstTime(Date.parse(s.startsAt))}–${kstTime(Date.parse(s.endsAt))} · ${userName(s.userId)}`

  const cell = (vehicleId: string, day: string) =>
    schedules
      .filter(s => s.vehicleId === vehicleId && kstDate(Date.parse(s.startsAt)) === day)
      .sort((a, b) => a.startsAt.localeCompare(b.startsAt))

  return (
    <section>
      <header className="screen-head">
        <div>
          <h2>배차</h2>
          <p className="muted">
            누가 언제 어느 차를 쓰는지 등록하면 그 차로 보내져요. 차는 운전자 선택 화면에서 이 사람을 맨 앞에 보여 줘요.
          </p>
        </div>
      </header>

      <form className="card form" onSubmit={submit}>
        <div className="row">
          <label className="field">
            <span>차량</span>
            <select value={form.vehicleId} onChange={set('vehicleId')}>
              {vehicles.length === 0 && <option value="">(차량 없음)</option>}
              {vehicles.map(v => (
                <option key={v.vehicleId} value={v.vehicleId}>
                  {v.plateNo}
                  {v.model ? ` · ${v.model}` : ''}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            <span>운전자</span>
            <select value={form.userId} onChange={set('userId')}>
              <option value="">(미정)</option>
              {users.map(u => (
                <option key={u.userId} value={u.userId}>
                  {u.name}
                  {u.dept ? ` · ${u.dept}` : ''}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            <span>날짜</span>
            <input type="date" value={form.date} onChange={set('date')} />
          </label>
          <label className="field narrow">
            <span>시작</span>
            <select value={form.start} onChange={set('start')}>
              {TIMES.slice(0, -1).map(t => (
                <option key={t} value={t}>
                  {timeLabel(t)}
                </option>
              ))}
            </select>
          </label>
          <label className="field narrow">
            <span>끝</span>
            <select value={form.end} onChange={set('end')}>
              {TIMES.filter(t => t > form.start).map(t => (
                <option key={t} value={t}>
                  {timeLabel(t)}
                </option>
              ))}
            </select>
          </label>
          <div className="field">
            <span>빠르게</span>
            <div className="presets">
              {PRESETS.map(p => (
                <button
                  key={p.label}
                  type="button"
                  className={`chip-button ${form.start === p.start && form.end === p.end ? 'on' : ''}`}
                  onClick={() => setForm(f => ({ ...f, start: p.start, end: p.end }))}
                >
                  {p.label}
                </button>
              ))}
            </div>
          </div>
          <div className="field end">
            <button type="submit" className="button" disabled={busy}>
              {busy ? '등록 중…' : '배차 등록'}
            </button>
          </div>
        </div>
      </form>

      <ErrorBox error={error} />
      {notice && <p className="notice">{notice}</p>}

      <div className="week-nav">
        <button className="button secondary" onClick={() => setWeek(addDays(week, -7))}>
          ‹ 지난주
        </button>
        <strong>
          {dayLabel(days[0])} – {dayLabel(days[6])}
        </strong>
        <button className="button secondary" onClick={() => setWeek(addDays(week, 7))}>
          다음 주 ›
        </button>
        <button className="link" onClick={() => setWeek(mondayOf(todayKst()))}>
          이번 주
        </button>
      </div>

      <div className="week-scroll">
        <table className="table week">
          <thead>
            <tr>
              <th>차량</th>
              {days.map(d => (
                <th key={d} className={d === todayKst() ? 'today' : ''}>
                  {dayLabel(d)}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {vehicles.length === 0 && (
              <tr>
                <td colSpan={8} className="empty">
                  등록된 차량이 없어요.
                </td>
              </tr>
            )}
            {vehicles.map(v => (
              <tr key={v.vehicleId}>
                <th className="vehicle">{v.plateNo}</th>
                {days.map(d => (
                  <td key={d} className={d === todayKst() ? 'today' : ''}>
                    {cell(v.vehicleId, d).map(s => (
                      <button key={s.scheduleId} className="slot" title="눌러서 지우기" onClick={() => remove(s)}>
                        <span className="slot-time">
                          {kstTime(Date.parse(s.startsAt))}–{kstTime(Date.parse(s.endsAt))}
                        </span>
                        <span>{userName(s.userId)}</span>
                      </button>
                    ))}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="muted small">배차를 누르면 지울 수 있어요.</p>
    </section>
  )
}
