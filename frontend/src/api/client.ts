import axios, { type AxiosInstance } from 'axios'
import { auth } from '../lib/auth'

const client: AxiosInstance = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
  timeout: 15_000,
})

// Attach access token on every request
client.interceptors.request.use((config) => {
  const token = auth.getToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// On 401: try refresh once, then redirect to login.
// IMPORTANT: only do this for protected routes, NOT for /auth/* calls.
let refreshing: Promise<string> | null = null

client.interceptors.response.use(
  (res) => res,
  async (err) => {
    const original = err.config

    // Don't intercept auth endpoints or already-retried requests
    const isAuthEndpoint = original?.url?.startsWith('/auth/')
    if (err.response?.status === 401 && !original._retry && !isAuthEndpoint) {
      original._retry = true
      const refresh = auth.getRefresh()

      if (refresh) {
        if (!refreshing) {
          refreshing = axios
            .post<import('./types').AuthResponse>(
              '/api/auth/refresh',
              { refreshToken: refresh },
            )
            .then(({ data }) => {
              auth.save(data)
              return data.accessToken
            })
            .catch((e) => {
              auth.clear()
              window.location.href = '/login'
              throw e
            })
            .finally(() => { refreshing = null })
        }

        try {
          const newToken = await refreshing!
          original.headers.Authorization = `Bearer ${newToken}`
          return client(original)
        } catch {
          // already redirected above
        }
      } else {
        // No refresh token — redirect to login
        auth.clear()
        window.location.href = '/login'
      }
    }

    return Promise.reject(err)
  },
)

export default client
