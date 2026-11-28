/* 首屏防闪烁：在 body 渲染前把主题类挂上，避免深色用户看到白屏闪一下 */
(function () {
  try {
    var saved = localStorage.getItem('sc-theme');
    var root = document.documentElement;
    if (saved === 'dark') {
      root.classList.add('dark');
      root.setAttribute('data-theme', 'dark');
      var meta = document.querySelector('meta[name="theme-color"]');
      if (meta) meta.setAttribute('content', '#1B1A22');
    } else {
      root.setAttribute('data-theme', 'light');
    }
  } catch (e) {
    /* 存储不可用时保持默认浅色 */
  }
})();
