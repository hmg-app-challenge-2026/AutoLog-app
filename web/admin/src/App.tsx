import { useEffect, useState, type FormEvent } from 'react'
import { api, ApiError, getToken, setToken, SESSION_EXPIRED, type Me } from './api'
import { VehiclesScreen } from './VehiclesScreen'
import { SchedulesScreen } from './SchedulesScreen'
import { MailScreen } from './MailScreen'
import { PurposesScreen } from './PurposesScreen'
import { UsersScreen } from './UsersScreen'
import { ErrorBox } from './ErrorBox'

// 운영 웹 (F-50~54). 회사마다 관리자 한 명이 이메일·비밀번호로 로그인하고, 자기 회사 데이터만 본다.
// 운행·비용 화면은 없다 — 운행 데이터는 서버에 없음(차 Room이 원본). 수신자는 [메일 설정] 화면 안에 있다.
const SCREENS = [
  { key: 'vehicles', label: '차량', id: 'F-50', ready: true },
  { key: 'purposes', label: '목적', id: 'F-08', ready: true },
  { key: 'schedules', label: '배차', id: 'F-53', ready: true },
  { key: 'users', label: '직원', id: 'F-51', ready: true },
  { key: 'mail', label: '메일 설정', id: 'F-24·F-54', ready: true },
  { key: 'account', label: '내 계정', id: '', ready: true },
] as const

type ScreenKey = (typeof SCREENS)[number]['key']

export default function App() {
  const [me, setMe] = useState<Me | null>(null)
  const [checking, setChecking] = useState(() => getToken() !== '')
  const [current, setCurrent] = useState<ScreenKey>('vehicles')

  // 저장된 세션이 아직 살아 있는지 확인
  useEffect(() => {
    if (!checking) return
    api
      .me()
      .then(setMe)
      .catch(() => setToken(''))
      .finally(() => setChecking(false))
  }, [checking])

  // 어느 화면에서든 403이면 로그인으로
  useEffect(() => {
    const onExpired = () => setMe(null)
    window.addEventListener(SESSION_EXPIRED, onExpired)
    return () => window.removeEventListener(SESSION_EXPIRED, onExpired)
  }, [])

  if (checking) return <div className="login muted">확인 중…</div>
  if (!me) return <Entrance onDone={setMe} />

  const logout = async () => {
    try {
      await api.logout()
    } catch {
      // 이미 끝난 세션이어도 이 브라우저에서는 지운다
    }
    setToken('')
    setMe(null)
  }

  return (
    <div className="layout">
      <nav className="nav">
        <div className="brand">
          AutoLog 운영
          <small className="org">{me.orgName}</small>
        </div>
        {SCREENS.map(s => (
          <button
            key={s.key}
            className={`nav-item ${s.key === current ? 'active' : ''}`}
            disabled={!s.ready}
            onClick={() => setCurrent(s.key)}
          >
            {s.label}
            {!s.ready && <small> 준비 중</small>}
          </button>
        ))}
        <div className="who">
          <span className="muted small">{me.email}</span>
          <button className="link" onClick={logout}>
            로그아웃
          </button>
        </div>
      </nav>
      <main className="main">
        {current === 'vehicles' && <VehiclesScreen />}
        {current === 'purposes' && <PurposesScreen />}
        {current === 'schedules' && <SchedulesScreen />}
        {current === 'users' && <UsersScreen />}
        {current === 'mail' && <MailScreen />}
        {current === 'account' && <AccountScreen me={me} />}
      </main>
    </div>
  )
}

/** 로그인 / 회사 가입 전환. */
function Entrance({ onDone }: { onDone: (me: Me) => void }) {
  const [mode, setMode] = useState<'login' | 'signup'>('login')
  return mode === 'login' ? (
    <Login onDone={onDone} onSignup={() => setMode('signup')} />
  ) : (
    <Signup onDone={onDone} onLogin={() => setMode('login')} />
  )
}

/** 회사 관리자 로그인. */
function Login({ onDone, onSignup }: { onDone: (me: Me) => void; onSignup: () => void }) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<ApiError | null>(null)
  const [busy, setBusy] = useState(false)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (!email.trim() || !password) return setError(new ApiError(400, 'FORM', '이메일과 비밀번호를 입력해 주세요'))
    setBusy(true)
    setError(null)
    try {
      const r = await api.login(email.trim(), password)
      setToken(r.token)
      onDone({ email: r.email, orgName: r.orgName })
    } catch (err) {
      setError(err as ApiError)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="login">
      <form className="card form" onSubmit={submit}>
        <h2>AutoLog 운영</h2>
        <p className="muted">회사 관리자 계정으로 로그인하세요.</p>
        <label className="field">
          <span>이메일</span>
          <input type="email" autoComplete="username" value={email} onChange={e => setEmail(e.target.value)} autoFocus />
        </label>
        <label className="field">
          <span>비밀번호</span>
          <input
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={e => setPassword(e.target.value)}
          />
        </label>
        <ErrorBox error={error} />
        <button type="submit" className="button" disabled={busy}>
          {busy ? '확인 중…' : '로그인'}
        </button>
        <p className="muted small switch">
          처음이신가요?{' '}
          <button type="button" className="link" onClick={onSignup}>
            회사 가입
          </button>
        </p>
        <p className="muted small">비밀번호를 잊었으면 AutoLog 운영 담당자에게 문의하세요.</p>
      </form>
    </div>
  )
}

/** 회사 가입: 회사와 첫 관리자(가입한 사람)를 한 번에 만들고 바로 들어간다. */
function Signup({ onDone, onLogin }: { onDone: (me: Me) => void; onLogin: () => void }) {
  const [form, setForm] = useState({ orgName: '', email: '', password: '', confirm: '' })
  const [error, setError] = useState<ApiError | null>(null)
  const [busy, setBusy] = useState(false)

  const set = (k: keyof typeof form) => (e: { target: { value: string } }) => setForm({ ...form, [k]: e.target.value })

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    if (!form.orgName.trim()) return setError(new ApiError(400, 'FORM', '회사 이름을 입력해 주세요'))
    if (!form.email.trim()) return setError(new ApiError(400, 'FORM', '이메일을 입력해 주세요'))
    if (form.password.length < 8) return setError(new ApiError(400, 'FORM', '비밀번호는 8자 이상이어야 해요'))
    if (form.password !== form.confirm) return setError(new ApiError(400, 'FORM', '비밀번호가 서로 달라요'))
    setBusy(true)
    try {
      const r = await api.signup(form.orgName.trim(), form.email.trim(), form.password)
      setToken(r.token)
      onDone({ email: r.email, orgName: r.orgName })
    } catch (err) {
      setError(err as ApiError)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="login">
      <form className="card form" onSubmit={submit}>
        <h2>회사 가입</h2>
        <p className="muted">가입하면 바로 차를 등록하고 연결 코드를 만들 수 있어요. 가입한 분이 회사 관리자가 됩니다.</p>
        <label className="field">
          <span>회사 이름</span>
          <input value={form.orgName} onChange={set('orgName')} placeholder="예: 가나다물류" autoFocus />
        </label>
        <label className="field">
          <span>관리자 이메일</span>
          <input type="email" autoComplete="username" value={form.email} onChange={set('email')} />
        </label>
        <label className="field">
          <span>비밀번호 (8자 이상)</span>
          <input type="password" autoComplete="new-password" value={form.password} onChange={set('password')} />
        </label>
        <label className="field">
          <span>비밀번호 확인</span>
          <input type="password" autoComplete="new-password" value={form.confirm} onChange={set('confirm')} />
        </label>
        <ErrorBox error={error} />
        <button type="submit" className="button" disabled={busy}>
          {busy ? '가입 중…' : '가입하고 시작하기'}
        </button>
        <p className="muted small switch">
          이미 계정이 있나요?{' '}
          <button type="button" className="link" onClick={onLogin}>
            로그인
          </button>
        </p>
      </form>
    </div>
  )
}

/** 내 계정: 비밀번호 바꾸기. 바꾸면 다른 브라우저의 로그인은 끊긴다. */
function AccountScreen({ me }: { me: Me }) {
  const [form, setForm] = useState({ current: '', next: '', confirm: '' })
  const [error, setError] = useState<ApiError | null>(null)
  const [notice, setNotice] = useState('')
  const [busy, setBusy] = useState(false)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    setNotice('')
    if (!form.current || !form.next) return setError(new ApiError(400, 'FORM', '비밀번호를 입력해 주세요'))
    if (form.next.length < 8) return setError(new ApiError(400, 'FORM', '새 비밀번호는 8자 이상이어야 해요'))
    if (form.next !== form.confirm) return setError(new ApiError(400, 'FORM', '새 비밀번호가 서로 달라요'))
    setBusy(true)
    try {
      await api.changePassword(form.current, form.next)
      setForm({ current: '', next: '', confirm: '' })
      setNotice('비밀번호를 바꿨어요. 다른 곳에서 로그인한 것은 끊겼어요.')
    } catch (err) {
      setError(err as ApiError)
    } finally {
      setBusy(false)
    }
  }

  const set = (k: keyof typeof form) => (e: { target: { value: string } }) => setForm({ ...form, [k]: e.target.value })

  return (
    <section>
      <header className="screen-head">
        <div>
          <h2>내 계정</h2>
          <p className="muted">
            {me.orgName} · {me.email}
          </p>
        </div>
      </header>
      <form className="card form narrow-form" onSubmit={submit}>
        <label className="field">
          <span>지금 비밀번호</span>
          <input type="password" autoComplete="current-password" value={form.current} onChange={set('current')} />
        </label>
        <label className="field">
          <span>새 비밀번호 (8자 이상)</span>
          <input type="password" autoComplete="new-password" value={form.next} onChange={set('next')} />
        </label>
        <label className="field">
          <span>새 비밀번호 확인</span>
          <input type="password" autoComplete="new-password" value={form.confirm} onChange={set('confirm')} />
        </label>
        <ErrorBox error={error} />
        {notice && <p className="notice">{notice}</p>}
        <button type="submit" className="button" disabled={busy}>
          {busy ? '바꾸는 중…' : '비밀번호 바꾸기'}
        </button>
      </form>
    </section>
  )
}
