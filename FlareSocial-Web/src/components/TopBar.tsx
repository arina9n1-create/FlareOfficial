import { Bell, PlusSquare, MessageCircle, Wallet } from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'
import { useState, useEffect } from 'react'
import { supabase } from '../lib/supabase'

const TopBar = () => {
  const [profile, setProfile] = useState<any>(null)
  const navigate = useNavigate()

  useEffect(() => {
    supabase.auth.getUser().then(({ data: { user } }) => {
      if (user) {
        supabase.from('app_users').select('*').eq('uid', user.id).single()
          .then(({ data }) => setProfile(data))
      }
    })
  }, [])

  return (
    <header className="fixed top-0 left-0 right-0 z-50 flex h-16 items-center justify-center border-b border-gray-100 bg-white/80 backdrop-blur-md">
      <div className="flex w-full max-w-4xl items-center justify-between px-4">
        <div className="flex cursor-pointer items-center gap-2 transition-all active:scale-95" onClick={() => navigate('/')}>
          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-gradient-to-br from-primary via-secondary to-flareBlue font-black italic text-white shadow-lg shadow-primary/20">
            F
          </div>
          <div className="flex flex-col -gap-1">
            <span className="leading-none text-xl font-black italic text-primary">Flare</span>
            <span className="text-[8px] font-black uppercase tracking-[0.2em] text-secondary">Official</span>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <Link to="/wallet" className="flex items-center gap-2 rounded-full border border-gray-100 bg-gray-50 px-3 py-1.5 transition-all hover:bg-gray-100 active:scale-95">
             <Wallet className="h-4 w-4 text-primary" />
             <span className="text-[10px] font-black text-gray-700">Wallet</span>
          </Link>

          <button className="rounded-full p-2 transition-all hover:bg-gray-100 active:scale-90">
            <PlusSquare className="h-5 w-5 text-gray-700" />
          </button>

          <button className="relative rounded-full p-2 transition-all hover:bg-gray-100 active:scale-90">
            <Bell className="h-5 w-5 text-gray-700" />
            <div className="absolute right-2 top-2 h-2 w-2 rounded-full border-2 border-white bg-red-500" />
          </button>

          <Link to="/chat" className="rounded-full p-2 transition-all hover:bg-gray-100 active:scale-90">
            <MessageCircle className="h-5 w-5 text-gray-700" />
          </Link>

          <div
            onClick={() => navigate('/profile')}
            className="ml-1 h-8 w-8 cursor-pointer rounded-full border-2 border-primary/20 overflow-hidden transition-all active:scale-90"
          >
             <img src={profile?.avatarPath || `https://ui-avatars.com/api/?name=${profile?.name}&background=random`} className="h-full w-full object-cover" alt="" />
          </div>
        </div>
      </div>
    </header>
  )
}

export default TopBar