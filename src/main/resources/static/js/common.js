/* 通用 AJAX 封装（携带 CSRF token） */
function getCsrfToken() {
    var m = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
    return m ? decodeURIComponent(m[1]) : '';
}

function postJSON(url, data, onSuccess, onFail) {
    var body = new URLSearchParams();
    for (var k in data) { if (data.hasOwnProperty(k)) body.append(k, data[k]); }
    fetch(url, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
            'X-XSRF-TOKEN': getCsrfToken()
        },
        body: body.toString()
    }).then(function (r) { return r.json(); })
      .then(function (res) {
          if (res.code === 200) { if (onSuccess) onSuccess(res); }
          else { if (onFail) onFail(res); else alert(res.message); }
      }).catch(function (e) { if (onFail) onFail({ message: '网络异常' }); });
}

/* 显示提示 */
function showAlert(id, type, msg) {
    var el = document.getElementById(id);
    if (!el) return;
    el.className = 'alert alert-' + type;
    el.style.display = 'block';
    el.textContent = msg;
}
function hideAlert(id) {
    var el = document.getElementById(id);
    if (el) el.style.display = 'none';
}

/* 延迟跳转 */
function redirectLater(url, ms) {
    setTimeout(function () { window.location.href = url; }, ms || 800);
}
