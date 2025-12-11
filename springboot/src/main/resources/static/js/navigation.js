// 导航和交互功能 - 现代化UI交互

// DOM元素
const navbar = document.querySelector('.navbar');
const navbarToggle = document.querySelector('.navbar-toggle');
const navbarMobile = document.querySelector('.navbar-mobile');
const navbarMobileClose = document.querySelector('.navbar-mobile-close');
const navbarOverlay = document.querySelector('.navbar-overlay');
const navbarLinks = document.querySelectorAll('.navbar-link');
const navbarMobileLinks = document.querySelectorAll('.navbar-mobile-link');

// 导航栏滚动效果
function handleNavbarScroll() {
  if (window.scrollY > 50) {
    navbar.classList.add('navbar-scrolled');
  } else {
    navbar.classList.remove('navbar-scrolled');
  }
}

// 打开移动菜单
function openMobileMenu() {
  navbarMobile.classList.add('open');
  navbarOverlay.classList.add('active');
  document.body.style.overflow = 'hidden'; // 防止背景滚动
}

// 关闭移动菜单
function closeMobileMenu() {
  navbarMobile.classList.remove('open');
  navbarOverlay.classList.remove('active');
  document.body.style.overflow = ''; // 恢复背景滚动
}

// 高亮当前激活的导航链接
function highlightActiveLink() {
  const currentPath = window.location.pathname;
  
  // 桌面导航
  navbarLinks.forEach(link => {
    const href = link.getAttribute('href');
    if (currentPath === href || (currentPath === '/' && href === '/index.html')) {
      link.classList.add('active');
    } else {
      link.classList.remove('active');
    }
  });
  
  // 移动导航
  navbarMobileLinks.forEach(link => {
    const href = link.getAttribute('href');
    if (currentPath === href || (currentPath === '/' && href === '/index.html')) {
      link.classList.add('active');
    } else {
      link.classList.remove('active');
    }
  });
}

// 初始化导航功能
function initNavigation() {
  // 添加滚动事件监听
  window.addEventListener('scroll', handleNavbarScroll);
  
  // 初始化滚动状态
  handleNavbarScroll();
  
  // 高亮当前链接
  highlightActiveLink();
  
  // 如果存在移动导航元素，添加事件监听
  if (navbarToggle) {
    navbarToggle.addEventListener('click', openMobileMenu);
  }
  
  if (navbarMobileClose) {
    navbarMobileClose.addEventListener('click', closeMobileMenu);
  }
  
  if (navbarOverlay) {
    navbarOverlay.addEventListener('click', closeMobileMenu);
  }
  
  // 移动导航链接点击事件
  navbarMobileLinks.forEach(link => {
    link.addEventListener('click', closeMobileMenu);
  });
  
  // 添加响应式处理
  window.addEventListener('resize', () => {
    // 在桌面视图下确保移动菜单关闭
    if (window.innerWidth > 768) {
      closeMobileMenu();
    }
  });
}

// 按钮加载状态功能
function setupButtonLoading() {
  const buttons = document.querySelectorAll('.btn');
  
  buttons.forEach(button => {
    button.addEventListener('click', function() {
      // 不处理禁用状态的按钮
      if (this.disabled) return;
      
      // 不处理文字按钮和轮廓按钮
      if (this.classList.contains('btn-text') || this.classList.contains('btn-outline')) return;
      
      // 临时保存按钮内容
      const originalContent = this.innerHTML;
      
      // 添加加载状态
      this.classList.add('btn-loading');
      this.disabled = true;
      
      // 模拟操作完成后恢复按钮状态
      // 实际项目中应根据异步操作完成情况来恢复
      setTimeout(() => {
        this.classList.remove('btn-loading');
        this.disabled = false;
        this.innerHTML = originalContent;
      }, 1500);
    });
  });
}

// 页面淡入效果
function setupPageFade() {
  document.body.style.opacity = '0';
  document.body.style.transition = 'opacity 0.5s ease';
  
  window.addEventListener('DOMContentLoaded', () => {
    setTimeout(() => {
      document.body.style.opacity = '1';
    }, 100);
  });
}

// 平滑滚动功能
function setupSmoothScroll() {
  document.querySelectorAll('a[href^="#"]').forEach(anchor => {
    anchor.addEventListener('click', function(e) {
      e.preventDefault();
      
      const targetId = this.getAttribute('href');
      if (targetId === '#') return;
      
      const targetElement = document.querySelector(targetId);
      if (targetElement) {
        const navbarHeight = navbar.offsetHeight;
        const targetPosition = targetElement.getBoundingClientRect().top + window.pageYOffset - navbarHeight;
        
        window.scrollTo({
          top: targetPosition,
          behavior: 'smooth'
        });
      }
    });
  });
}

// 图片懒加载
function setupLazyLoading() {
  const lazyImages = document.querySelectorAll('img[data-src]');
  
  if ('IntersectionObserver' in window) {
    const imageObserver = new IntersectionObserver((entries, observer) => {
      entries.forEach(entry => {
        if (entry.isIntersecting) {
          const image = entry.target;
          image.src = image.dataset.src;
          image.removeAttribute('data-src');
          imageObserver.unobserve(image);
        }
      });
    });
    
    lazyImages.forEach(image => {
      imageObserver.observe(image);
    });
  } else {
    // 降级处理：立即加载所有图片
    lazyImages.forEach(image => {
      image.src = image.dataset.src;
      image.removeAttribute('data-src');
    });
  }
}

// 执行所有初始化函数
function init() {
  // 等待DOM加载完成
  document.addEventListener('DOMContentLoaded', () => {
    initNavigation();
    setupButtonLoading();
    setupPageFade();
    setupSmoothScroll();
    setupLazyLoading();
  });
}

// 导出初始化函数供其他模块使用
window.App = window.App || {};
window.App.init = init;

// 自动初始化
init();