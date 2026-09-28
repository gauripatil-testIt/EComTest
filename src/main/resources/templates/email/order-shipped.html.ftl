<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8"/>
    <title>Order Shipped</title>
</head>
<body>
    <h1>Your order is on its way, ${customerName}!</h1>
    <p>Your order has been <strong>shipped</strong>.</p>
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
    <#if trackingNumber??>
    <p>Tracking Number: <strong>${trackingNumber}</strong></p>
    </#if>
    <p>We'll notify you once it's delivered.</p>
</body>
</html>
