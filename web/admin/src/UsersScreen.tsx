import { useEffect, useRef, useState, type FormEvent } from 'react'
import { api, ApiError, type User } from './api'
import { ErrorBox } from './ErrorBox'

const EMPTY = { name: '', dept: '' }

/**
 * 직원 (F-51). 차를 쓰는 사람을 등록한다. 저장하면 연결된 모든 차에 자동으로 들어가
 * 차 화면의 운전자 선택과 배차에 쓰인다(F-43). 여러 명은 CSV(이름·부서)로 한 번에 올린다.
 * 이메일은 받지 않는다(2026-11-15 — 쓰는 곳이 없는 개인정보).
 */
export function UsersScreen() {
  const [users, setUsers] = useState<User[] | null>(null)
  const [form, setForm] = useState(EMPTY)
  const [editing, setEditing] = useState<{ id: string; name: string; dept: string } | null>(null)
  const [error, setError] = useState<ApiError | null>(null)
  const [notice, setNotice] = useState('')
  const [busy, setBusy] = useState(false)
  const fileRef = useRef<HTMLInputElement>(null)

  const load = () =>
    api
      .users()
      .then(list => setUsers([...list].sort((a, b) => a.name.localeCompare(b.name, 'ko'))))
      .catch(e => setError(e as ApiError))

  useEffect(() => {
    load()
  }, [])

  const run = async (work: () => Promise<void>) => {
    setError(null)
    setNotice('')
    setBusy(true)
    try {
      await work()
    } catch (err) {
      setError(err as ApiError)
    } finally {
      setBusy(false)
    }
  }

  const add = (e: FormEvent) => {
    e.preventDefault()
    const name = form.name.trim()
    if (!name) return setError(new ApiError(400, 'FORM', '이름을 입력해 주세요'))
    run(async () => {
      await api.createUser({ name, dept: form.dept.trim() || undefined })
      setForm(EMPTY)
      setNotice(`${name}님을 등록했어요. 연결된 차에는 다음 동기화 때 들어가요.`)
      await load()
    })
  }

  const save = () => {
    if (!editing) return
    if (!editing.name.trim()) return setError(new ApiError(400, 'FORM', '이름을 입력해 주세요'))
    run(async () => {
      await api.updateUser(editing.id, { name: editing.name.trim(), dept: editing.dept.trim() })
      setEditing(null)
      await load()
    })
  }

  const remove = (u: User) => {
    if (!confirm(`${u.name}님을 직원에서 뺄까요?\n이 직원이 들어간 배차도 함께 지워지고, 연결된 차에서도 다음 동기화 때 빠져요.`)) return
    run(async () => {
      await api.deleteUser(u.userId)
      setNotice(`${u.name}님을 뺐어요.`)
      await load()
    })
  }

  const importCsv = (file: File | undefined) => {
    if (!file) return
    run(async () => {
      const r = await api.importUsers(file)
      setNotice(`CSV에서 ${r.imported}명을 등록했어요.`)
      await load()
    })
    if (fileRef.current) fileRef.current.value = ''
  }

  return (
    <section>
      <header className="screen-head">
        <div>
          <h2>직원</h2>
          <p className="muted">
            차를 쓰는 사람을 등록하세요. 연결된 모든 차의 운전자 목록과 배차에 자동으로 들어가요.
          </p>
        </div>
        <div>
          <input
            ref={fileRef}
            type="file"
            accept=".csv,text/csv"
            hidden
            onChange={e => importCsv(e.target.files?.[0])}
          />
          <button className="button secondary" disabled={busy} onClick={() => fileRef.current?.click()}>
            CSV로 여러 명 올리기
          </button>
        </div>
      </header>

      <form className="card form" onSubmit={add}>
        <div className="row">
          <label className="field grow">
            <span>이름</span>
            <input value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} placeholder="예: 홍길동" />
          </label>
          <label className="field grow">
            <span>부서 (선택)</span>
            <input value={form.dept} onChange={e => setForm({ ...form, dept: e.target.value })} placeholder="예: 영업팀" />
          </label>
          <button type="submit" className="button" disabled={busy}>
            {busy ? '저장 중…' : '추가'}
          </button>
        </div>
        <p className="muted small">CSV는 첫 줄에 이름,부서(또는 name,dept)를 쓰고 한 줄에 한 명씩 적어요.</p>
      </form>

      <ErrorBox error={error} />
      {notice && <p className="notice">{notice}</p>}

      {users && (
        <table className="table">
          <thead>
            <tr>
              <th>이름</th>
              <th>부서</th>
              <th className="right">관리</th>
            </tr>
          </thead>
          <tbody>
            {users.length === 0 && (
              <tr>
                <td colSpan={3} className="empty">
                  아직 등록한 직원이 없어요.
                </td>
              </tr>
            )}
            {users.map(u =>
              editing?.id === u.userId ? (
                <tr key={u.userId}>
                  <td>
                    <input value={editing.name} onChange={e => setEditing({ ...editing, name: e.target.value })} />
                  </td>
                  <td>
                    <input value={editing.dept} onChange={e => setEditing({ ...editing, dept: e.target.value })} />
                  </td>
                  <td className="right">
                    <button className="link" disabled={busy} onClick={save}>
                      저장
                    </button>
                    {' · '}
                    <button className="link" onClick={() => setEditing(null)}>
                      취소
                    </button>
                  </td>
                </tr>
              ) : (
                <tr key={u.userId}>
                  <td>
                    <strong>{u.name}</strong>
                  </td>
                  <td>{u.dept ?? '—'}</td>
                  <td className="right">
                    <button
                      className="link"
                      onClick={() =>
                        setEditing({ id: u.userId, name: u.name, dept: u.dept ?? '' })
                      }
                    >
                      수정
                    </button>
                    {' · '}
                    <button className="link danger" onClick={() => remove(u)}>
                      빼기
                    </button>
                  </td>
                </tr>
              ),
            )}
          </tbody>
        </table>
      )}
    </section>
  )
}
