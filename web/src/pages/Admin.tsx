import { useState, useEffect } from 'react'
import { supabase } from '../lib/supabase'
import {
  ShieldCheck, Users, FileText, Database,
  Settings as SettingsIcon, AlertCircle, CheckCircle,
  XCircle, ChevronRight, LayoutDashboard, Crown
} from 'lucide-react'
import { useNavigate } from 'react-router-dom'

const Admin = () => {
  const [isAdmin, setIsAdmin] = useState(false)
  const [stats, setStats] = useState<any>({ users: 0, posts: 0, reports: 0 })
  const [loading, setLoading] = useState(true)
  const navigate = useNavigate()

  useEffect(() => {
    const checkAccess = async () => {
      const { data: { user } } = await supabase.auth.getUser()
      if (!user) {
        navigate('/')
        return
      }

      const { data: profile } = await supabase.from('app_users').select('role').eq('uid', user.id).single()
      if (profile?.role === 'ADMIN' || profile?.role === 'SUPER_ADMIN') {
        setIsAdmin(true)
        // Load basic stats mirroring Android logic
        const { count: userCount } = await supabase.from('app_users').select('*', { count: 'exact', head: true })
        const { count: postCount } = await supabase.from('posts').select('*', { count: 'exact', head: true })
        setStats({ users: userCount || 0, posts: postCount || 0, reports: 0 })
      } else {
        navigate('/')
      }
      setLoading(false)
    }

    checkAccess()
  }, [])

  if (loading) return <div className="flex h-60 w-full items-center justify-center"><div className="h-6 w-6 animate-spin rounded-full border-2 border-primary border-t-transparent"></div></div>

  const menuItems = [
    { icon: Users, label: "User Management", desc: "View, ban, or update permissions", color: "text-blue-500", bg: "bg-blue-50" },
    { icon: FileText, label: "Content Moderation", desc: "Review reported posts & reels", color: "text-orange-500", bg: "bg-orange-50" },
    { icon: ShieldCheck, label: "Admin Permissions", desc: "Manage staff & moderator roles", color: "text-purple-500", bg: "bg-purple-50" },
    { icon: Database, label: "System Storage", desc: "Clean old media & cache logs", color: "text-green-500", bg: "bg-green-50" },
    { icon: AlertCircle, label: "Active Reports", desc: "Urgent community flags", color: "text-red-500", bg: "bg-red-50" },
    { icon: SettingsIcon, label: "Global Settings", desc: "Update app-wide configurations", color: "text-gray-500", bg: "bg-gray-50" },
  ]

  return (
    <div className="mx-auto w-full max-w-4xl px-4 py-8 pb-24 min-h-screen">
      <div className="flex items-center gap-3 mb-8">
        <div className="h-12 w-12 rounded-2xl bg-primary flex items-center justify-center text-white shadow-lg shadow-primary/30">
           <Crown className="h-7 w-7" />
        </div>
        <div>
           <h1 className="text-2xl font-black italic">Staff Control Panel</h1>
           <p className="text-[10px] font-black uppercase tracking-widest text-textSecondary opacity-60 flex items-center gap-1">
             <ShieldCheck className="h-3 w-3" /> Secure Access · v1.0 (Web)
           </p>
        </div>
      </div>

      {/* Quick Stats Grid */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-10">
        {[
          { label: "Total Users", val: stats.users, icon: Users, color: "text-blue-600" },
          { label: "Active Posts", val: stats.posts, icon: FileText, color: "text-green-600" },
          { label: "Reports", val: 0, icon: AlertCircle, color: "text-red-600" },
          { label: "System Health", val: "100%", icon: Database, color: "text-purple-600" },
        ].map((item, idx) => (
          <div key={idx} className="bg-white rounded-3xl p-5 border border-gray-100 shadow-sm">
             <div className={`${item.color} mb-3`}><item.icon className="h-5 w-5" /></div>
             <h4 className="text-2xl font-black">{item.val}</h4>
             <p className="text-[9px] font-bold text-textSecondary uppercase tracking-wider">{item.label}</p>
          </div>
        ))}
      </div>

      {/* Management Menu */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {menuItems.map((item, idx) => (
          <div key={idx} className="group flex items-center justify-between p-5 bg-white rounded-[2rem] border border-gray-50 shadow-sm cursor-pointer hover:shadow-md transition-all active:scale-98">
            <div className="flex items-center gap-5">
              <div className={`h-14 w-14 rounded-2xl ${item.bg} ${item.color} flex items-center justify-center transition-transform group-hover:rotate-6`}>
                 <item.icon className="h-7 w-7" />
              </div>
              <div>
                 <h4 className="font-black text-base">{item.label}</h4>
                 <p className="text-xs text-textSecondary font-medium">{item.desc}</p>
              </div>
            </div>
            <ChevronRight className="h-5 w-5 text-gray-300 group-hover:text-primary transition-colors" />
          </div>
        ))}
      </div>

      {/* Logs Preview Card */}
      <div className="mt-10 bg-white rounded-[2.5rem] p-8 border border-gray-100 shadow-sm">
         <div className="flex items-center justify-between mb-6">
            <h3 className="text-lg font-black italic">Recent Activity Log</h3>
            <span className="text-[10px] font-black bg-gray-50 px-3 py-1 rounded-full text-textSecondary">View All</span>
         </div>

         <div className="flex flex-col items-center justify-center py-12 text-center opacity-40">
            <LayoutDashboard className="h-12 w-12 mb-4" />
            <p className="text-sm font-bold">Secure activity logging is enabled.<br/>Recent staff actions will appear here.</p>
         </div>
      </div>
    </div>
  )
}

export default Admin