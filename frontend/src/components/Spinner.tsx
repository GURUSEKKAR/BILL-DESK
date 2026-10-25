import { cn } from '../lib/utils'

interface Props { size?: 'sm' | 'md' | 'lg'; className?: string }

export function Spinner({ size = 'md', className }: Props) {
  return (
    <div className={cn(
      'animate-spin rounded-full border-2 border-gray-200 border-t-blue-600',
      { 'h-4 w-4': size === 'sm', 'h-6 w-6': size === 'md', 'h-10 w-10': size === 'lg' },
      className,
    )} />
  )
}

export function PageSpinner() {
  return (
    <div className="flex items-center justify-center py-24">
      <Spinner size="lg" />
    </div>
  )
}
