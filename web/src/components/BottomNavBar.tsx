import { Home, Search, PlayCircle, MessageCircle, User, Settings } from 'lucide-react'
import { NavLink, useLocation } from 'react-router-dom'
import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'

function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

const BottomNavBar = () => {
  const location = useLocation()
  const isReelsPage = location.pathname === '/reels'

  const navItems = [
    { icon: Home, label: 'Home', path: '/' },
    { icon: Search, label: 'Search', path: '/search' },
    { icon: PlayCircle, label: 'Reels', path: '/reels' },
    { icon: MessageCircle, label: 'Chat', path: '/chat' },
    { icon: User, label: 'Profile', path: '/profile' },
    { icon: Settings, label: 'Settings', path: '/settings' },
  ]

  return (
    <nav className={cn(
      "fixed bottom-0 left-0 right-0 z-50 flex items-center justify-center border-t border-gray-100 bg-white/90 px-4 py-2 backdrop-blur-md transition-all",
      isReelsPage ? "bg-black/80 border-white/10" : "bg-white/90"
    )}>
      <div className="flex w-full max-w-2xl items-center justify-between">
        {navItems.map((item) => (
          <NavLink
            key={item.path}
            to={item.path}
            className={({ isActive }) =>
              cn(
                'flex flex-col items-center justify-center gap-1 rounded-2xl p-2 transition-all active:scale-90',
                isActive
                  ? (isReelsPage ? 'text-white' : 'text-primary')
                  : (isReelsPage ? 'text-white/40' : 'text-gray-400')
              )
            }
          >
            <item.icon className="h-5 w-5" />
            <span className="text-[9px] font-bold uppercase tracking-wider">{item.label}</span>
          </NavLink>
        ))}
      </div>
    </nav>
  )
}

export default BottomNavBar