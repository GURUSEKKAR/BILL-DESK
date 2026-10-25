import { cn } from '../lib/utils'

interface Props extends React.HTMLAttributes<HTMLDivElement> {
  children: React.ReactNode
}

export function Card({ className, children, ...props }: Props) {
  return (
    <div
      className={cn('bg-white rounded-xl border border-gray-200 shadow-sm', className)}
      {...props}
    >
      {children}
    </div>
  )
}

export function CardHeader({ className, children, ...props }: Props) {
  return (
    <div className={cn('px-5 py-4 border-b border-gray-100', className)} {...props}>
      {children}
    </div>
  )
}

export function CardBody({ className, children, ...props }: Props) {
  return (
    <div className={cn('p-5', className)} {...props}>
      {children}
    </div>
  )
}
