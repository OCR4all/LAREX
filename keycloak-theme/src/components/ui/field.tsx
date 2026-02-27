import { cn } from "@/lib/utils"
import { forwardRef } from "react"

export interface FieldGroupProps extends React.HTMLAttributes<HTMLDivElement> {}

export const FieldGroup = forwardRef<HTMLDivElement, FieldGroupProps>(
  ({ className, ...props }, ref) => (
    <div
      ref={ref}
      className={cn("flex flex-col gap-6", className)}
      {...props}
    />
  )
)
FieldGroup.displayName = "FieldGroup"

export interface FieldProps extends React.HTMLAttributes<HTMLDivElement> {}

export const Field = forwardRef<HTMLDivElement, FieldProps>(
  ({ className, ...props }, ref) => (
    <div
      ref={ref}
      className={cn("space-y-2", className)}
      {...props}
    />
  )
)
Field.displayName = "Field"

export interface FieldLabelProps extends React.LabelHTMLAttributes<HTMLLabelElement> {}

export const FieldLabel = forwardRef<HTMLLabelElement, FieldLabelProps>(
  ({ className, ...props }, ref) => (
    <label
      ref={ref}
      className={cn(
        "text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70",
        className
      )}
      {...props}
    />
  )
)
FieldLabel.displayName = "FieldLabel"

export interface FieldDescriptionProps extends React.HTMLAttributes<HTMLParagraphElement> {}

export const FieldDescription = forwardRef<HTMLParagraphElement, FieldDescriptionProps>(
  ({ className, ...props }, ref) => (
    <p
      ref={ref}
      className={cn("text-sm text-muted-foreground", className)}
      {...props}
    />
  )
)
FieldDescription.displayName = "FieldDescription"

export interface FieldSeparatorProps extends React.HTMLAttributes<HTMLDivElement> {
  children?: React.ReactNode
}

export const FieldSeparator = forwardRef<HTMLDivElement, FieldSeparatorProps>(
  ({ className, children, ...props }, ref) => (
    <div
      ref={ref}
      className={cn("relative", className)}
      {...props}
    >
      <div className="absolute inset-0 flex items-center">
        <span className="w-full border-t" />
      </div>
      <div className="relative flex justify-center text-xs uppercase">
        <span className="bg-background px-2 text-muted-foreground">
          {children}
        </span>
      </div>
    </div>
  )
)
FieldSeparator.displayName = "FieldSeparator"