// 중계 서버 관리자 API (F-50~54). nginx와 vite dev 서버가 /admin/api/* → relay /admin/* 로 넘긴다.
// 인증: 회사 관리자가 이메일·비밀번호로 로그인하면 받는 세션 토큰을 X-Admin-Token으로 보낸다(2026-11-13).
// 토큰은 이 브라우저에만 저장하고, 서버가 403(만료·로그아웃)을 주면 지우고 로그인 화면으로 돌아간다.

const BASE = '/admin/api'
const TOKEN_KEY = 'autolog.adminSession'
const LEGACY_KEY = 'autolog.adminToken' // 예전 공용 토큰 — 더는 쓰지 않으므로 지운다

/** 세션이 끝났을 때(403) App이 로그인 화면으로 돌아가도록 알린다. */
export const SESSION_EXPIRED = 'autolog:session-expired'

export function getToken(): string {
  try {
    localStorage.removeItem(LEGACY_KEY)
    return localStorage.getItem(TOKEN_KEY) ?? ''
  } catch {
    return ''
  }
}

export function setToken(token: string) {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token)
    else localStorage.removeItem(TOKEN_KEY)
  } catch {
    // 저장소를 못 쓰는 브라우저 — 새로고침하면 다시 로그인
  }
}

export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
    public lines: { line: number; message: string }[] = [],
  ) {
    super(message)
  }
}

async function request<T>(method: string, path: string, body?: unknown, contentType = 'application/json'): Promise<T> {
  let res: Response
  try {
    res = await fetch(BASE + path, {
      method,
      headers: {
        'X-Admin-Token': getToken(),
        ...(body !== undefined ? { 'Content-Type': contentType } : {}),
      },
      body: body === undefined ? undefined : contentType === 'application/json' ? JSON.stringify(body) : (body as BodyInit),
    })
  } catch {
    throw new ApiError(0, 'NETWORK', '서버에 연결하지 못했어요. 서버가 켜져 있는지 확인해 주세요')
  }
  const text = await res.text()
  const json = text ? safeParse(text) : undefined
  if (!res.ok) {
    const j = (json ?? {}) as { code?: string; message?: string; errors?: { line: number; message: string }[] }
    const fallback = res.status === 403 ? '로그인이 끝났어요. 다시 로그인해 주세요' : `요청이 실패했어요 (HTTP ${res.status})`
    if (res.status === 403 && !path.startsWith('/auth/login') && !path.startsWith('/auth/signup')) {
      setToken('')
      window.dispatchEvent(new Event(SESSION_EXPIRED))
    }
    throw new ApiError(res.status, j.code ?? 'HTTP', j.message ?? fallback, j.errors ?? [])
  }
  return json as T
}

function safeParse(text: string): unknown {
  try {
    return JSON.parse(text)
  } catch {
    return undefined
  }
}

// ---- 타입 (relay 응답과 같은 모양) ----

export type PlaceKind = 'OFFICE' | 'BRANCH' | 'CLIENT' | 'GAS_STATION'

export interface Place {
  placeId: string
  label: string
  kind: PlaceKind
  lat: number
  lng: number
  radiusM: number
  defaultPurpose: string | null
}

export interface PlaceInput {
  label: string
  kind: PlaceKind
  address?: string
  lat?: number
  lng?: number
  radiusM?: number
  defaultPurpose?: string
}

export interface Vehicle {
  vehicleId: string
  plateNo: string
  model: string | null
  vehicleCode: string
  registered: boolean
  lastInboxAt: string | null
}

export interface ConnectCode {
  code: string
  expiresAt: string
}

export interface LoginResult {
  token: string
  expiresAt: string
  email: string
  orgName: string
}

export interface Me {
  email: string
  orgName: string
}

export interface User {
  userId: string
  name: string
  dept: string | null
}

export interface Schedule {
  scheduleId: string
  vehicleId: string
  userId: string | null
  startsAt: string
  endsAt: string
  destPlaceId: string | null
  purposeDetail: string | null
}

export interface ScheduleInput {
  vehicleId: string
  userId?: string
  startsAt: string
  endsAt: string
  destPlaceId?: string
  purposeDetail?: string
}

// ---- 호출 ----

/** 회사 메일 발신 계정. 비밀번호는 서버가 돌려주지 않는다. */
export type MailAccount = {
  host: string
  port: number
  username: string
  fromName: string | null
  startTls: boolean
  updatedAt: string
}

export type MailAccountInput = {
  host: string
  port: number
  username: string
  /** 비우면 저장된 비밀번호를 그대로 쓴다. */
  password?: string
  fromName?: string
}

/** 운행기록부 받는 사람(회사 지정). 차 설정의 받는 사람 목록에 "회사"로 들어간다. */
export type Recipient = {
  id: number
  email: string
  name: string | null
}

export const api = {
  login: (email: string, password: string) => request<LoginResult>('POST', '/auth/login', { email, password }),
  signup: (orgName: string, email: string, password: string) =>
    request<LoginResult>('POST', '/auth/signup', { orgName, email, password }),
  me: () => request<Me>('GET', '/auth/me'),
  logout: () => request<void>('POST', '/auth/logout'),
  changePassword: (currentPassword: string, newPassword: string) =>
    request<void>('POST', '/auth/password', { currentPassword, newPassword }),

  places: () => request<Place[]>('GET', '/places'),
  createPlace: (p: PlaceInput) => request<Place>('POST', '/places', p),
  deletePlace: (id: string) => request<void>('DELETE', `/places/${id}`),
  importPlaces: (csv: File) => request<{ imported: number }>('POST', '/places/import', csv, 'text/csv'),

  /** 계정이 없으면 undefined(204). */
  mailAccount: () => request<MailAccount | undefined>('GET', '/mail-account'),
  saveMailAccount: (a: MailAccountInput) => request<MailAccount>('PUT', '/mail-account', a),
  deleteMailAccount: () => request<void>('DELETE', '/mail-account'),
  recipients: () => request<Recipient[]>('GET', '/recipients'),
  addRecipient: (email: string, name?: string) => request<void>('POST', '/recipients', { email, name }),
  deleteRecipient: (id: number) => request<void>('DELETE', `/recipients/${id}`),
  vehicles: () => request<Vehicle[]>('GET', '/vehicles'),
  createVehicle: (plateNo: string, model?: string) =>
    request<{ vehicleId: string; vehicleCode: string }>('POST', '/vehicles', { plateNo, model }),
  connectCode: (vehicleId: string) => request<ConnectCode>('POST', `/vehicles/${vehicleId}/connect-code`),
  revokeDevice: (vehicleId: string) => request<void>('DELETE', `/vehicles/${vehicleId}/device`),
  users: () => request<User[]>('GET', '/users'),

  schedules: (from: string, to: string) =>
    request<Schedule[]>('GET', `/schedules?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`),
  createSchedule: (s: ScheduleInput) => request<Schedule>('POST', '/schedules', s),
  deleteSchedule: (id: string) => request<void>('DELETE', `/schedules/${id}`),
}
