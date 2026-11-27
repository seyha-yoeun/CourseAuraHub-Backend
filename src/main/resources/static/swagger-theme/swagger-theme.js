/*
 * Course Aura Hub: the light/dark switch on the API docs.
 *
 * SwaggerThemeTransformer loads this after setting <html data-theme> from the saved choice or the system setting;
 * swagger-dark.css does the styling. Until someone uses the switch, the docs follow the system setting live.
 */
(function () {
  var KEY = 'swagger-theme';
  var root = document.documentElement;
  var system = window.matchMedia ? window.matchMedia('(prefers-color-scheme: dark)') : null;
  var ICON_ATTRS = 'viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"';
  var SUN = '<svg ' + ICON_ATTRS + '><circle cx="12" cy="12" r="4"/><path d="M12 2v2"/><path d="M12 20v2"/>'
    + '<path d="m4.93 4.93 1.41 1.41"/><path d="m17.66 17.66 1.41 1.41"/><path d="M2 12h2"/><path d="M20 12h2"/>'
    + '<path d="m6.34 17.66-1.41 1.41"/><path d="m19.07 4.93-1.41 1.41"/></svg>';
  var MOON = '<svg ' + ICON_ATTRS + '><path d="M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z"/></svg>';

  function saved() {
    try {
      var value = localStorage.getItem(KEY);
      return value === 'dark' || value === 'light' ? value : null;
    } catch (e) {
      return null;
    }
  }

  function current() {
    return saved() || (system && system.matches ? 'dark' : 'light');
  }

  var button = document.createElement('button');
  button.type = 'button';
  button.className = 'cah-theme-toggle';

  function apply(theme) {
    root.setAttribute('data-theme', theme);
    var label = theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode';
    button.innerHTML = theme === 'dark' ? SUN : MOON;
    button.setAttribute('aria-label', label);
    button.title = label;
  }

  button.addEventListener('click', function () {
    var next = current() === 'dark' ? 'light' : 'dark';
    try {
      localStorage.setItem(KEY, next);
    } catch (e) {
      // Private browsing without storage: the switch still works until the page reloads
    }
    apply(next);
  });

  if (system) {
    var follow = function () {
      if (!saved()) apply(current());
    };
    if (system.addEventListener) system.addEventListener('change', follow);
    else if (system.addListener) system.addListener(follow);
  }

  apply(current());
  document.body.appendChild(button);
})();
