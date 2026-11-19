import { Menu } from 'lucide-react'
import type { ReactNode } from 'react'
import { useState } from 'react'
import { Sidebar } from '../navigation/Sidebar'
export function AppShell({ children }: { children: ReactNode }) { const [open, setOpen] = useState(false); return <div className="min-h-screen bg-canvas"><Sidebar open={open} onClose={() => setOpen(false)} /><div className="lg:pl-64"><header className="flex h-14 items-center border-b border-line bg-white px-4 lg:hidden"><button className="focus-ring rounded p-2 text-ink" onClick={() => setOpen(true)} aria-label="Open navigation"><Menu /></button><span className="ml-3 font-semibold text-navy">AeroRoster</span></header><main className="mx-auto max-w-[1600px] px-4 py-6 sm:px-6 lg:px-8 lg:py-8">{children}</main></div></div> }
