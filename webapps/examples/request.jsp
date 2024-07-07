<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8" />
  <title>Request headers</title>
  <style type="text/css">
  body { font-size: 12px; }
  h1 { font-size:18px; border-bottom: 1px solid #ccc; }
  th, td { padding: 2px; }
  </style>
</head>
<body>
  <h1>List of HTTP Request Headers</h1>
  <table>
	<tr>
	  <th align="right">Request URI:</th>
	  <td><%=request.getMethod()%> <%=request.getRequestURI()%></td>
	</tr>
	<%
     java.util.Enumeration<String> names = request.getHeaderNames();
     while (names.hasMoreElements()) {
	   String name = names.nextElement();
	%>
	<tr>
	  <th nowrap align="right"> <%=name%>:</th>
	  <td><%=request.getHeader(name)%></td>
	</tr>
   <% } %>
   </table>
 </body>
</html>