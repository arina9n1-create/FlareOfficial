import { Routes, Route, Navigate, useLocation } from 'react-router-dom'
import { useState, useEffect } from 'react'
import { supabase } from './lib/supabase'
import Home from './pages/Home'
import Search from './pages/Search'
import Chat from './pages/Chat'
import Reels from './pages/Reels'
import Profile from './pages/Profile'
import Wallet from './pages/Wallet'
import Settings from './pages/Settings'
import Admin from './pages/Admin'
import Auth from './pages/Auth'
import BottomNavBar from './components/BottomNavBar'
import TopBar from './components/TopBar'

function App() {
  const [session, setSession] = useState<any>(null)
  const [loading, setLoading] = useState(true)
  const location = useLocation()

  useEffect(() => {
    supabase.auth.getSession().then(({ data: { session } }) => {
      setSession(session)
      setLoading(false)
    })

    const {
      data: { subscription },
    } = supabase.auth.onAuthStateChange((_event, session) => {
      setSession(session)
    })

    return () => subscription.unsubscribe()
  }, [])

  if (loading) {
    return (
      <div className="flex h-screen w-full items-center justify-center bg-background">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent"></div>
      </div>
    )
  }

  if (!session) {
    return <Auth />
  }

  const isReelsPage = location.pathname === '/reels'
  const isFullScreenPage = ['/auth', '/reels'].includes(location.pathname)

  return (
    <div className={`flex min-h-screen w-full flex-col bg-background ${isReelsPage ? 'bg-black text-white' : ''}`}>
      {!isFullScreenPage && <TopBar />}

      <main className={`flex-1 w-full ${!isFullScreenPage ? 'pb-20 pt-16' : ''}`}>
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/search" element={<Search />} />
          <Route path="/chat" element={<Chat />} />
          <Route path="/reels" element={<Reels />} />
          <Route path="/profile" element={<Profile />} />
          <Route path="/wallet" element={<Wallet />} />
          <Route path="/settings" element={<Settings />} />
          <Route path="/admin" element={<Admin />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>

      <BottomNavBar />
    </div>
  )
}

export default App