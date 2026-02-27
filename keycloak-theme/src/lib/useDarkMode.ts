import { useEffect, useState } from "react";

/**
 * Gets a cookie value by name
 */
function getCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp('(^| )' + name + '=([^;]+)'));
  return match ? match[2] : null;
}

/**
 * Hook to read dark mode preference from multiple sources:
 * 1. URL parameter (?dark=true) - highest priority
 * 2. nuxt-color-mode cookie (from Nuxt frontend) - matches the cookie name used by @nuxtjs/color-mode
 * 3. localStorage (for persistence within Keycloak session)
 * 
 * This enables theme carry-over from the Nuxt frontend to the Keycloak login pages.
 */
export function useDarkMode() {
  const [isDark, setIsDark] = useState(false);

  useEffect(() => {
    // Check multiple sources for dark mode preference (priority order)
    
    // 1. URL parameter (highest priority - explicit override)
    const urlParams = new URLSearchParams(window.location.search);
    const darkParam = urlParams.get('dark');
    
    // 2. Nuxt color-mode cookie (set by @nuxtjs/color-mode in the frontend)
    const colorModeCookie = getCookie('nuxt-color-mode');
    
    // 3. localStorage (for persistence within Keycloak session)
    const localStorageValue = localStorage.getItem('keycloak-dark-mode');
    
    // Determine if dark mode should be enabled
    let shouldBeDark = false;
    
    if (darkParam !== null) {
      // URL param takes priority (explicit true/false)
      shouldBeDark = darkParam === 'true';
    } else if (colorModeCookie && colorModeCookie !== 'system') {
      // Check Nuxt's color-mode cookie (ignore if set to 'system')
      shouldBeDark = colorModeCookie === 'dark';
    } else if (localStorageValue !== null) {
      // Fall back to localStorage
      shouldBeDark = localStorageValue === 'true';
    } else {
      // Final fallback: check system preference
      shouldBeDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
    }

    setIsDark(shouldBeDark);

    // Apply dark mode class to document
    if (shouldBeDark) {
      document.documentElement.classList.add('dark');
      document.body.classList.add('dark');
    } else {
      document.documentElement.classList.remove('dark');
      document.body.classList.remove('dark');
    }

    // Store in localStorage for persistence within Keycloak session
    localStorage.setItem('keycloak-dark-mode', String(shouldBeDark));
  }, []);

  return isDark;
}