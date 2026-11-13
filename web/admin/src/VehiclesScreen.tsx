import { useEffect, useState, type FormEvent } from 'react'
import { api, ApiError, type ConnectCode, type Vehicle } from './api'
import { ErrorBox } from './ErrorBox'
import { kstDate, kstTime } from './time'

/**
 * 차량 (F-50). 회사 차를 등록하고, 차에 넣을 연결 코드(6자리, 24시간, 1회)를 만든다.
 * 차 화면 [설정 → 차량 정보 → 연결 코드 입력]에 이 코드를 넣으면 그 차가 이 회사에 연결된다.
 */
export function VehiclesScreen() {
  const [vehicles, setVehicles] = useState<Vehicle[]>([])
  const [error, setError] = useState<ApiError | null>(null)
  const [notice, setNotice] = useState('')
  const [busy, setBusy] = useState(false)
  const [form, setForm] = useState({ plateNo: '', model: '' })
  const [issued, setIssued] = useState<{ vehicle: Vehicle; code: ConnectCode } | null>(null)

  const load = () =>
    api
      .vehicles()
      .then(setVehicles)
      .catch(e => setError(e as ApiError))

  useEffect(() => {
    load()
  }, [])

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    setNotice('')
    const plateNo = form.plateNo.trim()
    if (!plateNo) return setError(new ApiError(400, 'FORM', '차량 번호를 입력해 주세요'))
    setBusy(true)
    try {
      const created = await api.createVehicle(plateNo, form.model.trim() || undefined)
      setForm({ plateNo: '', model: '' })
      const list = await api.vehicles()
      setVehicles(list)
      const v = list.find(x => x.vehicleId === created.vehicleId)
      // 등록하면 바로 연결 코드까지 — 관리자가 할 다음 일이 그것뿐이다
      if (v) await issue(v)
    } catch (err) {
      setError(err as ApiError)
    } finally {
      setBusy(false)
    }
  }

  const issue = async (v: Vehicle) => {
    setError(null)
    setNotice('')
    try {
      setIssued({ vehicle: v, code: await api.connectCode(v.vehicleId) })
    } catch (err) {
      setError(err as ApiError)
    }
  }

  const revoke = async (v: Vehicle) => {
    if (!confirm(`${v.plateNo}의 연결을 끊을까요?\n차는 다음 동기화부터 서버에 연결할 수 없고, 새 연결 코드로 다시 연결해야 해요.`)) return
    setError(null)
    try {
      await api.revokeDevice(v.vehicleId)
      setNotice(`${v.plateNo}의 연결을 끊었어요.`)
      await load()
    } catch (err) {
      setError(err as ApiError)
    }
  }

  return (
    <section>
      <header className="screen-head">
        <div>
          <h2>차량</h2>
          <p className="muted">차를 등록하고 연결 코드를 만들어 차 화면의 [설정 → 차량 정보 → 연결 코드 입력]에 넣으세요.</p>
        </div>
      </header>

      <form className="card form" onSubmit={submit}>
        <div className="row">
          <label className="field grow">
            <span>차량 번호</span>
            <input
              value={form.plateNo}
              onChange={e => setForm({ ...form, plateNo: e.target.value })}
              placeholder="예: 12가3456"
            />
          </label>
          <label className="field grow">
            <span>차종 (선택)</span>
            <input value={form.model} onChange={e => setForm({ ...form, model: e.target.value })} placeholder="예: 아이오닉 5" />
          </label>
          <button type="submit" className="button" disabled={busy}>
            {busy ? '등록 중…' : '등록하고 연결 코드 만들기'}
          </button>
        </div>
      </form>

      <ErrorBox error={error} />
      {notice && <p className="notice">{notice}</p>}

      {issued && (
        <div className="card code-card" role="status">
          <div>
            <p className="muted">{issued.vehicle.plateNo} 연결 코드</p>
            <p className="connect-code">{issued.code.code}</p>
            <p className="muted small">
              {kstDate(Date.parse(issued.code.expiresAt))} {kstTime(Date.parse(issued.code.expiresAt))}까지 한 번만 쓸 수 있어요.
            </p>
          </div>
          <div className="code-actions">
            <button className="button secondary" onClick={() => navigator.clipboard?.writeText(issued.code.code)}>
              복사
            </button>
            <button className="link" onClick={() => setIssued(null)}>
              닫기
            </button>
          </div>
        </div>
      )}

      <table className="table">
        <thead>
          <tr>
            <th>차량 번호</th>
            <th>차종</th>
            <th>상태</th>
            <th>마지막 동기화</th>
            <th className="right">관리</th>
          </tr>
        </thead>
        <tbody>
          {vehicles.length === 0 && (
            <tr>
              <td colSpan={5} className="empty">
                아직 등록한 차가 없어요.
              </td>
            </tr>
          )}
          {vehicles.map(v => (
            <tr key={v.vehicleId}>
              <td>
                <strong>{v.plateNo}</strong>
              </td>
              <td>{v.model ?? '—'}</td>
              <td>
                <span className={`chip ${v.registered ? 'state-on' : 'state-off'}`}>{v.registered ? '연결됨' : '연결 안 됨'}</span>
              </td>
              <td className="mono">
                {v.lastInboxAt ? `${kstDate(Date.parse(v.lastInboxAt))} ${kstTime(Date.parse(v.lastInboxAt))}` : '—'}
              </td>
              <td className="right">
                <button className="link" onClick={() => issue(v)}>
                  {v.registered ? '다시 연결 코드' : '연결 코드 만들기'}
                </button>
                {v.registered && (
                  <>
                    {' · '}
                    <button className="link danger" onClick={() => revoke(v)}>
                      연결 끊기
                    </button>
                  </>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  )
}
