import { useState, useEffect } from 'react'
import { supabase } from '../lib/supabase'
import {
  User, Lock, Bell, Shield, Wallet, Diamond,
  HelpCircle, Info, LogOut, ChevronRight, Moon
} from 'lucide-react'
import { useNavigate } from 'react-router-dom'

const Settings = () => {
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

  const handleLogout = async () => {
    await supabase.auth.signOut()
    navigate('/')
  }

  const sections = [
    {
      title: "Creator Monetization & Rewards",
      items: [
        { icon: Wallet, label: "My Wallet 💳", sub: "Add funds, withdraw & history", path: "/wallet" },
        { icon: Diamond, label: "Monetization 💎", sub: "Earnings from your content", path: "/monetization" },
      ]
    },
    {
      title: "Account Settings",
      items: [
        { icon: User, label: "Edit Profile", sub: "Change name, bio & photos", path: "/profile" },
        { icon: Shield, label: "Privacy & Safety", sub: "Manage who sees your content" },
        { icon: Lock, label: "Security", sub: "Password & 2FA" },
      ]
    },
    {
      title: "Preferences",
      items: [
        { icon: Bell, label: "Notifications", sub: "Control alerts and sounds" },
        { icon: Moon, label: "Dark Mode", sub: "System default" },
      ]
    },
    {
      title: "Support & Legal",
      items: [
        { icon: HelpCircle, label: "Help & Support" },
        { icon: Info, label: "About App", sub: "v1.0.0 (Web)" },
      ]
    }
  ]

  return (
    <div className="mx-auto w-full max-w-2xl px-4 py-6 pb-24 min-h-screen">
      <h1 className="text-2xl font-black mb-8 px-2">Settings</h1>

      {/* Profile Summary Card */}
      <div
        onClick={() => navigate('/profile')}
        className="mb-8 flex items-center justify-between rounded-3xl bg-white p-5 shadow-sm border border-gray-100 cursor-pointer active:scale-98 transition-all"
      >
        <div className="flex items-center gap-4">
          <div className="h-14 w-14 rounded-full overflow-hidden border-2 border-primary/20">
             <img src={profile?.avatarPath || `https://ui-avatars.com/api/?name=${profile?.name}&background=random`} className="h-full w-full object-cover" alt="" />
          </div>
          <div>
            <h3 className="font-black text-base">{profile?.name || 'Loading...'}</h3>
            <p className="text-xs text-textSecondary font-medium">@{profile?.handle || '...'}</p>
          </div>
        </div>
        <ChevronRight className="h-5 w-5 text-gray-300" />
      </div>

      {/* Settings Sections */}
      <div className="space-y-8">
        {sections.map((section, idx) => (
          <div key={idx}>
            <h4 className="text-[10px] font-black uppercase tracking-widest text-textSecondary mb-3 px-4 opacity-60">
              {section.title}
            </h4>
            <div className="rounded-[2rem] bg-white overflow-hidden shadow-sm border border-gray-50">
              {section.items.map((item, i) => (
                <div
                  key={i}
                  onClick={() => (item as any).path && navigate((item as any).path)}
                  className={`flex items-center justify-between p-4 hover:bg-gray-50 cursor-pointer transition-all active:bg-gray-100 ${
                    i !== section.items.length - 1 ? 'border-b border-gray-50' : ''
                  }`}
                >
                  <div className="flex items-center gap-4">
                    <div className="flex h-10 w-10 items-center justify-center rounded-2xl bg-gray-50 text-gray-700">
                       <item.icon className="h-5 w-5" />
                    </div>
                    <div>
                       <p className="text-sm font-bold">{item.label}</p>
                       {item.sub && <p className="text-[10px] text-textSecondary font-medium">{item.sub}</p>}
                    </div>
                  </div>
                  <ChevronRight className="h-4 w-4 text-gray-300" />
                </div>
              ))}
            </div>
          </div>
        ))}
      </div>

      {/* Logout Button */}
      <button
        onClick={handleLogout}
        className="mt-12 flex w-full items-center justify-center gap-2 rounded-2xl bg-red-50 py-4 text-sm font-black text-red-500 transition-all active:scale-95 border border-red-100"
      >
        <LogOut className="h-5 w-5" /> Sign Out
      </button>

      <div className="mt-8 text-center">
        <p className="text-[10px] font-bold text-gray-400 uppercase tracking-widest">FlareOfficial Web Platform</p>
        <p className="text-[8px] text-gray-300 mt-1">© 2026 All rights reserved</p>
      </div>
    </div>
  )
}

export default Settings