var socket;

function submit() {
    let hostInput = document.getElementById("host");
    let portInput = document.getElementById("port");
    let contentInput = document.getElementById("content");

    let host = hostInput.value;
    let port = portInput.value;
    let content = contentInput.value;

    if (host == null || host.trim() == "") {
      alert("请输入IP地址")
      return;
    }

    if (port == null || port.trim() == "") {
      alert("请输入端口")
      return;
    }

    if (content == null || content.trim() == "") {
      alert("请输入内容");
      return;
    }

    if (socket != null && socket != undefined) {
        socket.close();
    }
   // Create WebSocket connection.
   socket = new WebSocket(`ws://${host}:${port}/test`);

   socket.onopen = function() {
    socket.send(content);
    alert("发送成功")
   };

   socket.onmessage = function(e) {
    console.log("Websocket message", e.data);
   };

   socket.onerror = function(e) {
    console.log("Websocket error", e)
    alert("连接失败")
  };

  socket.onclose = function() {
    console.log("Websocket close")
  }
}