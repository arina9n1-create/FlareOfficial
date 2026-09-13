import { useState, useEffect } from 'react'
import { supabase } from '../lib/supabase'
import { Wallet, Plus, ArrowUpRight, History, RefreshCcw, CreditCard } from 'lucide-react'
import { format } from 'date-fns'

const WalletPage = () => {
  const [wallet, setWallet] = useState<any>(null)
  const [transactions, setTransactions] = useState<any[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const loadWalletData = async () => {
      const { data: { user } } = await supabase.auth.getUser()
      if (!user) return

      try {
        // Mirroring Android MonetizationRepository logic
        const { data: walletData } = await supabase.from('monetization_wallets').select('*').eq('userId', user.id).single()
        const { data: txns } = await supabase.from('monetization_transactions').select('*').eq('userId', user.id).order('createdAt', { ascending: false })

        setWallet(walletData || { availableBalance: 0, totalAddedFunds: 0, totalWithdrawn: 0 })
        setTransactions(txns || [])
      } catch (error) {
        console.error('Error loading wallet:', error)
      } finally {
        setLoading(false)
      }
    }

    loadWalletData()
  }, [])

  if (loading) {
    return (
      <div className="flex h-60 w-full items-center justify-center">
        <div className="h-6 w-6 animate-spin rounded-full border-2 border-primary border-t-transparent"></div>
      </div>
    )
  }

  return (
    <div className="mx-auto w-full max-w-2xl px-4 py-6 pb-24">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-black italic text-primary flex items-center gap-2">
          <Wallet className="h-7 w-7" /> My Wallet
        </h1>
        <button className="rounded-full p-2 bg-gray-100 hover:bg-gray-200 transition-all active:rotate-180 duration-500">
          <RefreshCcw className="h-5 w-5 text-gray-600" />
        </button>
      </div>

      {/* Main Balance Card - Mirroring Android's Professional Hub */}
      <div className="relative overflow-hidden rounded-[2rem] bg-gradient-to-br from-[#6C5CE7] via-[#8E44AD] to-[#E84393] p-8 text-white shadow-xl shadow-primary/20">
        <div className="relative z-10">
          <p className="text-[10px] font-black uppercase tracking-[0.2em] opacity-80">Total Available Balance</p>
          <h2 className="mt-2 text-5xl font-black">${wallet?.availableBalance?.toFixed(2) || '0.00'}</h2>
          <p className="mt-4 text-xs font-bold opacity-70 flex items-center gap-2">
             <CreditCard className="h-3 w-3" /> Ready for withdrawal & boosts
          </p>

          <div className="mt-8 grid grid-cols-2 gap-4">
            <button className="flex items-center justify-center gap-2 rounded-2xl bg-white py-3.5 text-sm font-black text-primary transition-all active:scale-95 shadow-lg">
              <Plus className="h-4 w-4" /> Add Fund
            </button>
            <button className="flex items-center justify-center gap-2 rounded-2xl bg-white/20 backdrop-blur-md py-3.5 text-sm font-black text-white transition-all active:scale-95 border border-white/10">
              <ArrowUpRight className="h-4 w-4" /> Withdraw
            </button>
          </div>
        </div>

        {/* Background Decorative Circles */}
        <div className="absolute -right-10 -top-10 h-40 w-40 rounded-full bg-white/10 blur-3xl" />
        <div className="absolute -left-10 -bottom-10 h-40 w-40 rounded-full bg-white/10 blur-3xl" />
      </div>

      {/* Stats Row */}
      <div className="mt-6 grid grid-cols-2 gap-4">
        <div className="rounded-3xl bg-orange-50 p-5 border border-orange-100">
           <p className="text-[10px] font-black text-orange-600 uppercase tracking-wider">Total Added</p>
           <h4 className="mt-1 text-xl font-black text-orange-700">${wallet?.totalAddedFunds?.toFixed(2) || '0.00'}</h4>
        </div>
        <div className="rounded-3xl bg-red-50 p-5 border border-red-100">
           <p className="text-[10px] font-black text-red-600 uppercase tracking-wider">Total Withdraw</p>
           <h4 className="mt-1 text-xl font-black text-red-700">${wallet?.totalWithdrawn?.toFixed(2) || '0.00'}</h4>
        </div>
      </div>

      {/* Transaction History */}
      <div className="mt-10">
        <div className="flex items-center justify-between mb-4 px-2">
           <h3 className="text-lg font-black italic">Transaction History</h3>
           <History className="h-5 w-5 text-gray-400" />
        </div>

        {transactions.length > 0 ? (
          <div className="space-y-3">
            {transactions.map((txn) => (
              <div key={txn.id} className="flex items-center justify-between rounded-2xl bg-white p-4 shadow-sm border border-gray-50">
                <div className="flex items-center gap-4">
                  <div className={`flex h-10 w-10 items-center justify-center rounded-full ${
                    txn.type === 'WITHDRAWAL' ? 'bg-red-50 text-red-500' : 'bg-green-50 text-green-500'
                  }`}>
                    {txn.type === 'WITHDRAWAL' ? <ArrowUpRight className="h-5 w-5" /> : <Plus className="h-5 w-5" />}
                  </div>
                  <div>
                    <h4 className="text-sm font-bold">{txn.description || txn.type}</h4>
                    <p className="text-[10px] text-gray-400 font-medium">
                       {txn.createdAt ? format(new Date(txn.createdAt), 'MMM d, yyyy · hh:mm a') : 'Recently'}
                    </p>
                  </div>
                </div>
                <div className={`text-sm font-black ${txn.type === 'WITHDRAWAL' ? 'text-red-500' : 'text-green-500'}`}>
                   {txn.type === 'WITHDRAWAL' ? '-' : '+'}${txn.amount?.toFixed(2)}
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div className="flex flex-col items-center justify-center rounded-[2.5rem] bg-gray-50 p-12 text-center border-2 border-dashed border-gray-200">
            <div className="mb-4 rounded-full bg-white p-4 shadow-sm">
               <History className="h-8 w-8 text-gray-300" />
            </div>
            <p className="text-sm font-bold text-gray-400">No transactions yet</p>
          </div>
        )}
      </div>
    </div>
  )
}

export default WalletPage