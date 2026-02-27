import * as React from "react";
import { cva, type VariantProps } from "class-variance-authority";

import { cn } from "@/lib/utils";

const alertVariants = cva(
  "relative w-full rounded-lg border px-4 py-3 text-sm grid has-[>svg]:grid-cols-[calc(var(--spacing)*4)_1fr] grid-cols-[0_1fr] has-[>svg]:gap-x-3 gap-y-0.5 items-start [&>svg]:size-4 [&>svg]:translate-y-0.5 [&>svg]:text-current",
  {
    variants: {
      variant: {
        default: "bg-card text-card-foreground",
        destructive:
          "text-destructive bg-card [&>svg]:text-current *:data-[slot=alert-description]:text-destructive/90",
        success:
          "bg-green-100 text-green-800 border-green-200 [&>svg]:text-green-600 *:data-[slot=alert-description]:text-green-700",
        info: "bg-blue-100 text-blue-800 border-blue-200 [&>svg]:text-blue-600 *:data-[slot=alert-description]:text-blue-700",
        warning:
          "bg-yellow-100 text-yellow-800 border-yellow-200 [&>svg]:text-yellow-600 *:data-[slot=alert-description]:text-yellow-700",
        danger:
          "bg-red-100 text-red-800 border-red-200 [&>svg]:text-red-600 *:data-[slot=alert-description]:text-red-700",
        error:
          "bg-red-100 text-red-800 border-red-200 [&>svg]:text-red-600 *:data-[slot=alert-description]:text-red-700"
      }
    },
    defaultVariants: {
      variant: "default"
    }
  }
);

function Alert({
  className,
  variant,
  ...props
}: React.ComponentProps<"div"> & VariantProps<typeof alertVariants>) {
  return (
    <div
      data-slot="alert"
      role="alert"
      className={cn(alertVariants({ variant }), className)}
      {...props}
    />
  );
}

function AlertTitle({ className, ...props }: React.ComponentProps<"div">) {
  return (
    <div
      data-slot="alert-title"
      className={cn(
        "col-start-2 line-clamp-1 min-h-4 font-medium tracking-tight",
        className
      )}
      {...props}
    />
  );
}

function AlertDescription({ className, ...props }: React.ComponentProps<"div">) {
  return (
    <div
      data-slot="alert-description"
      className={cn(
        "text-muted-foreground col-start-2 grid justify-items-start gap-1 text-sm [&_p]:leading-relaxed",
        className
      )}
      {...props}
    />
  );
}

export { Alert, AlertTitle, AlertDescription };
