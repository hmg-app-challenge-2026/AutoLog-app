import { useEffect, useState, type FormEvent } from 'react'
import { api, ApiError, type MailAccount, type Recipient } from './api'
import { ErrorBox } from './ErrorBox'
import { kstDate, kstTime } from './time'

/**
 * 메일 설정 (F-24·F-54). 회사 발신 메일 계정은 하나 — 여기서 저장하면 회사의 모든 차에 자동으로 들어간다.
 * 차는 이 계정으로 운행기록부를 직접 보낸다(엑셀은 서버를 거치지 않음). 앱 비밀번호는 저장 후 다시 보여 주지 않는다.
 * 받는 사람도 여기서 정한다 — 회사의 모든 차와 새로 연결하는 차에 자동으로 들어간다(차에서 따로 추가한 사람은 그대로).
 */

/** 메일 주소 도메인으로 SMTP 서버를 미리 채운다. 모르는 도메인(회사 메일)은 직접 입력. */
const KNOWN_SMTP: Record<string, { host: string; port: number }> = {
  'gmail.com': { host: 'smtp.gmail.com', port: 587 },
  'naver.com': { host: 'smtp.naver.com', port: 587 },
  'daum.net': { host: 'smtp.daum.net', port: 465 },
  'hanmail.net': { host: 'smtp.daum.net', port: 465 },
  'kakao.com': { host: 'smtp.kakao.com', port: 465 },
  'outlook.com': { host: 'smtp.office365.com', port: 587 },
  'hotmail.com': { host: 'smtp.office365.com', port: 587 },
  'nate.com': { host: 'smtp.mail.nate.com', port: 465 },
}

const EMPTY = { username: '', host: '', port: '587', password: '', fromName: '' }

export function MailScreen() {
  const [account, setAccount] = useState<MailAccount | null>(null)
  const [loaded, setLoaded] = useState(false)
  const [form, setForm] = useState(EMPTY)
  const [error, setError] = useState<ApiError | null>(null)
  const [notice, setNotice] = useState('')
  const [busy, setBusy] = useState(false)

  const fill = (a: MailAccount | null) => {
    setAccount(a)
    setForm(a ? { username: a.username, host: a.host, port: String(a.port), password: '', fromName: a.fromName ?? '' } : EMPTY)
  }

  useEffect(() => {
    api
      .mailAccount()
      .then(a => fill(a ?? null))
      .catch(err => setError(err as ApiError))
      .finally(() => setLoaded(true))
  }, [])

  const set = (k: keyof typeof EMPTY) => (e: { target: { value: string } }) => setForm({ ...form, [k]: e.target.value })

  /** 메일 주소를 다 쓰면, 서버 칸이 비어 있을 때만 아는 도메인의 서버·포트를 채운다. */
  const suggestHost = () => {
    const domain = form.username.trim().toLowerCase().split('@')[1]
    const known = domain ? KNOWN_SMTP[domain] : undefined
    if (known && !form.host.trim()) setForm({ ...form, host: known.host, port: String(known.port) })
  }

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    setNotice('')
    if (!form.username.trim() || !form.host.trim()) {
      return setError(new ApiError(400, 'FORM', '메일 주소와 SMTP 서버를 입력해 주세요'))
    }
    if (!account && !form.password) return setError(new ApiError(400, 'FORM', '앱 비밀번호를 입력해 주세요'))
    const port = Number(form.port)
    if (!Number.isInteger(port) || port < 1 || port > 65535) {
      return setError(new ApiError(400, 'FORM', '포트가 올바르지 않아요'))
    }
    setBusy(true)
    try {
      const saved = await api.saveMailAccount({
        username: form.username.trim(),
        host: form.host.trim(),
        port,
        password: form.password || undefined,
        fromName: form.fromName.trim() || undefined,
      })
      fill(saved)
      setNotice('메일 서버에 로그인해 보고 저장했어요. 연결된 차는 다음 동기화 때 이 계정을 받아요.')
    } catch (err) {
      setError(err as ApiError)
    } finally {
      setBusy(false)
    }
  }

  const remove = async () => {
    if (!confirm('메일 계정을 지울까요?\n차들도 이 계정을 지워서, 다시 설정할 때까지 운행기록부를 메일로 보낼 수 없어요.')) return
    setError(null)
    setNotice('')
    setBusy(true)
    try {
      await api.deleteMailAccount()
      fill(null)
      setNotice('메일 계정을 지웠어요.')
    } catch (err) {
      setError(err as ApiError)
    } finally {
      setBusy(false)
    }
  }

  return (
    <section>
      <header className="screen-head">
        <div>
          <h2>메일 설정</h2>
          <p className="muted">
            운행기록부를 보낼 회사 메일 계정이에요. 여기서 한 번 정하면 연결된 모든 차에 자동으로 들어가요.
          </p>
        </div>
      </header>

      {loaded && account && (
        <div className="card code-card">
          <div>
            <span className="chip state-on">연결됨</span>
            <p style={{ margin: '8px 0 2px', fontWeight: 600 }}>{account.username}</p>
            <p className="muted small" style={{ marginTop: 0 }}>
              {account.host}:{account.port} · {savedAt(account.updatedAt)} 저장
            </p>
          </div>
          <button className="link danger" onClick={remove} disabled={busy}>
            지우기
          </button>
        </div>
      )}

      {loaded && (
        <form className="card form narrow-form" onSubmit={submit}>
          <label className="field">
            <span>보내는 메일 주소(아이디)</span>
            <input
              type="email"
              autoComplete="off"
              placeholder="autolog@company.co.kr"
              value={form.username}
              onChange={set('username')}
              onBlur={suggestHost}
            />
          </label>
          <div className="row">
            <label className="field grow">
              <span>SMTP 서버</span>
              <input placeholder="smtp.company.co.kr" value={form.host} onChange={set('host')} />
            </label>
            <label className="field narrow">
              <span>포트</span>
              <input inputMode="numeric" value={form.port} onChange={set('port')} />
            </label>
          </div>
          <label className="field">
            <span>앱 비밀번호{account ? ' (바꿀 때만 입력)' : ''}</span>
            <input
              type="password"
              autoComplete="new-password"
              placeholder={account ? '••••••••' : ''}
              value={form.password}
              onChange={set('password')}
            />
          </label>
          <label className="field">
            <span>보내는 사람 이름 (선택)</span>
            <input placeholder="AutoLog" value={form.fromName} onChange={set('fromName')} />
          </label>
          <p className="muted small">
            Gmail·네이버 같은 메일은 로그인 비밀번호 대신 메일 설정에서 만든 <b>앱 비밀번호</b>를 넣어요. 회사 메일은
            IT 담당자에게 SMTP 발신 계정을 받아 넣어요.
          </p>
          <ErrorBox error={error} />
          {notice && <p className="notice">{notice}</p>}
          <button type="submit" className="button" disabled={busy}>
            {busy ? '메일 서버 확인 중…' : account ? '확인하고 저장' : '확인하고 연결'}
          </button>
        </form>
      )}
      {!loaded && <ErrorBox error={error} />}

      <RecipientsCard />
    </section>
  )
}

/** 받는 사람 — 회사가 정한 목록. 바꾸면 연결된 모든 차에 다음 동기화 때 들어간다. */
function RecipientsCard() {
  const [list, setList] = useState<Recipient[] | null>(null)
  const [form, setForm] = useState({ email: '', name: '' })
  const [error, setError] = useState<ApiError | null>(null)
  const [busy, setBusy] = useState(false)

  const load = () =>
    api
      .recipients()
      .then(setList)
      .catch(err => setError(err as ApiError))

  useEffect(() => {
    load()
  }, [])

  const add = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    if (!form.email.trim()) return setError(new ApiError(400, 'FORM', '받는 사람 메일 주소를 입력해 주세요'))
    setBusy(true)
    try {
      await api.addRecipient(form.email.trim(), form.name.trim() || undefined)
      setForm({ email: '', name: '' })
      await load()
    } catch (err) {
      setError(err as ApiError)
    } finally {
      setBusy(false)
    }
  }

  const remove = async (r: Recipient) => {
    if (!confirm(`${r.name ?? r.email}을(를) 받는 사람에서 뺄까요?\n연결된 차에서도 다음 동기화 때 빠져요.`)) return
    setError(null)
    try {
      await api.deleteRecipient(r.id)
      await load()
    } catch (err) {
      setError(err as ApiError)
    }
  }

  return (
    <>
      <header className="screen-head" style={{ marginTop: 28 }}>
        <div>
          <h2>받는 사람</h2>
          <p className="muted">
            운행기록부를 받을 사람이에요. 여기 넣으면 연결된 모든 차와 새로 연결하는 차에 자동으로 들어가요.
          </p>
        </div>
      </header>
      <form className="card form" onSubmit={add}>
        <div className="row">
          <label className="field grow">
            <span>메일 주소</span>
            <input
              type="email"
              placeholder="accounting@company.co.kr"
              value={form.email}
              onChange={e => setForm({ ...form, email: e.target.value })}
            />
          </label>
          <label className="field">
            <span>이름 (선택)</span>
            <input placeholder="회계팀" value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} />
          </label>
          <button type="submit" className="button" disabled={busy}>
            {busy ? '추가 중…' : '추가'}
          </button>
        </div>
        <ErrorBox error={error} />
      </form>
      {list && (
        <table className="table">
          <thead>
            <tr>
              <th>이름</th>
              <th>메일 주소</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {list.length === 0 && (
              <tr>
                <td colSpan={3} className="empty">
                  아직 받는 사람이 없어요
                </td>
              </tr>
            )}
            {list.map(r => (
              <tr key={r.id}>
                <td>{r.name ?? '—'}</td>
                <td>{r.email}</td>
                <td className="right">
                  <button className="link danger" onClick={() => remove(r)}>
                    빼기
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </>
  )
}

function savedAt(iso: string): string {
  const ms = Date.parse(iso)
  return Number.isNaN(ms) ? '' : `${kstDate(ms)} ${kstTime(ms)}`
}
