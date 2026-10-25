import { formatINR } from '../lib/money'

interface Props {
  paise: number
  className?: string
  size?: 'sm' | 'base' | 'lg' | 'xl' | '2xl'
}

export function MoneyText({ paise, className = '', size = 'base' }: Props) {
  const sizes = {
    sm: 'text-sm',
    base: 'text-base',
    lg: 'text-lg font-semibold',
    xl: 'text-xl font-bold',
    '2xl': 'text-2xl font-bold',
  }
  return (
    <span className={`${sizes[size]} tabular-nums ${className}`}>
      {formatINR(paise)}
    </span>
  )
}
