/**
 * Token storage — both tokens in localStorage for simplicity.
 * (For production, keep access token in memory and refresh in httpOnly cookie.)
 */
import type { AuthResponse, Role } from '../api/types'

const ACCESS_KEY  = 'inv_access'
const REFRESH_KEY = 'inv_refresh'
const BIZ_KEY     = 'inv_biz'
const BIZ_NAME    = 'inv_biz_name'
const BIZ_CODE    = 'inv_biz_code'
const ROLE_KEY    = 'inv_role'
const COUNTER_KEY = 'inv_counter'
const COUNTER_CODE = 'inv_counter_code'

export const auth = {
  getToken:         () => localStorage.getItem(ACCESS_KEY),
  getRefresh:       () => localStorage.getItem(REFRESH_KEY),
  getBusinessId:    () => localStorage.getItem(BIZ_KEY),
  getBusinessName:  () => localStorage.getItem(BIZ_NAME),
  getBusinessCode:  () => localStorage.getItem(BIZ_CODE),
  getRole:          () => (localStorage.getItem(ROLE_KEY) as Role | null),
  getCounterId:     () => localStorage.getItem(COUNTER_KEY),
  getCounterCode:   () => localStorage.getItem(COUNTER_CODE),

  isOwner:   () => localStorage.getItem(ROLE_KEY) === 'OWNER',
  isCashier: () => localStorage.getItem(ROLE_KEY) === 'CASHIER',

  /** Persist a full auth response (login, counter-login, register, refresh). */
  save(data: AuthResponse) {
    localStorage.setItem(ACCESS_KEY,  data.accessToken)
    localStorage.setItem(REFRESH_KEY, data.refreshToken)
    localStorage.setItem(BIZ_KEY,     data.businessId)
    localStorage.setItem(BIZ_NAME,    data.businessName)
    localStorage.setItem(BIZ_CODE,    data.businessLoginCode ?? '')
    localStorage.setItem(ROLE_KEY,    data.role)
    setOrClear(COUNTER_KEY,  data.counterId)
    setOrClear(COUNTER_CODE, data.counterCode)
  },

  setAccessToken(token: string) {
    localStorage.setItem(ACCESS_KEY, token)
  },

  clear() {
    ;[ACCESS_KEY, REFRESH_KEY, BIZ_KEY, BIZ_NAME, BIZ_CODE, ROLE_KEY, COUNTER_KEY, COUNTER_CODE]
      .forEach((k) => localStorage.removeItem(k))
  },

  isLoggedIn: () => localStorage.getItem(ACCESS_KEY) !== null,
}

function setOrClear(key: string, value: string | null | undefined) {
  if (value) localStorage.setItem(key, value)
  else localStorage.removeItem(key)
}
