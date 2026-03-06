import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { GoogleLogin } from '@react-oauth/google'
import { useAuth } from '../auth/AuthContext'

export function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [error, setError] = useState<string | null>(null)

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-100">
      <div className="flex w-80 flex-col items-center gap-6 rounded-2xl bg-white p-8 shadow-lg">
        <div className="text-center">
          <h1 className="text-2xl font-bold text-gray-900">OutlinerGod</h1>
          <p className="mt-1 text-sm text-gray-500">Your self-hosted notes</p>
        </div>
        <GoogleLogin
          onSuccess={async credentialResponse => {
            setError(null)
            if (!credentialResponse.credential) {
              setError('Sign-in failed. Please try again.')
              return
            }
            try {
              await login(credentialResponse.credential)
              navigate('/')
            } catch {
              setError('Sign-in failed. Please try again.')
            }
          }}
          onError={() => {
            setError('Sign-in failed. Please try again.')
          }}
          use_fedcm_for_button
        />
        {error && (
          <p className="text-sm text-red-600">{error}</p>
        )}
      </div>
    </div>
  )
}
