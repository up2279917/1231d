export const initializeTheme = `
  (function() {
    try {
      const theme = localStorage.getItem('theme') || (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
      document.documentElement.classList.remove('dark', 'light');
      document.documentElement.classList.add(theme);
      document.body.classList.remove('dark', 'light');
      document.body.classList.add(theme);
      window.__INITIAL_THEME__ = theme;
    } catch (err) {
      console.error('Failed to initialize theme:', err);
    }
  })();
`;
