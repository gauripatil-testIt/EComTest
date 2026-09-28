<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8"/>
    <title>Order Confirmed</title>
</head>
<body>
    <h1>Your order has been confirmed, ${customerName}!</h1>
    <p>Great news — your order is now <strong>confirmed</strong> and being prepared.</p>
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
    <p>We'll let you know as soon as it ships.</p>
</body>
</html>
