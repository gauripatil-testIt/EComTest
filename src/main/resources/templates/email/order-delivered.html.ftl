<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8"/>
    <title>Order Delivered</title>
</head>
<body>
    <h1>Your order has arrived, ${customerName}!</h1>
    <p>Your order has been <strong>delivered</strong>. We hope you enjoy it!</p>
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
    <p>Thank you for shopping with us.</p>
</body>
</html>
