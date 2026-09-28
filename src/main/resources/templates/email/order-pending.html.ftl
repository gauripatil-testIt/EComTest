<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8"/>
    <title>Order Confirmation</title>
</head>
<body>
    <h1>Thank you for your order, ${customerName}!</h1>
    <p>We have received your order and it is now <strong>pending</strong>.</p>
    <table>
        <tr>
            <td>Order ID:</td>
            <td>${orderId?c}</td>
        </tr>
        <tr>
            <td>Product:</td>
            <td>${productName}</td>
        </tr>
        <tr>
            <td>Quantity:</td>
            <td>${quantity?c}</td>
        </tr>
        <tr>
            <td>Unit Price:</td>
            <td>${unitPrice}</td>
        </tr>
        <tr>
            <td>Total:</td>
            <td>${total}</td>
        </tr>
    </table>
    <p>We'll notify you again once your order is confirmed.</p>
</body>
</html>
