[![Codacy Badge](https://api.codacy.com/project/badge/Grade/e5478ccbaf4a4485baef769243da4f68)](https://www.codacy.com/app/Andee13/messenger?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=Andee13/messenger&amp;utm_campaign=Badge_Grade)

[![Build Status](https://semaphoreci.com/api/v1/andee13/messenger/branches/master/badge.svg)](https://semaphoreci.com/andee13/messenger)

# messenger

Общение сервера и клиента состоит из обработки пересланных объектов типа Message, представленных в XML-формате. Экземпляр данного класса может быть представлен как request (сообщение от клиента серверу, запрос на выполнение конкретного действия) либо же response (ответ сервера клиенту на request). За идентификацию типа сообщения отвечает final-поле  type : Type, инициализируемое при создании.
С свою очередь request- и response- сообщения также могут варьироваться в зависимости от смысловой нагрузки. Так, например, final-поле status : Enum<? Extends Enum> заполняется сразу же при создании объекта класса и является маркером сообщения.
Request-сообщения могут быть следующих типов AUTH, REGISTRATION, MESSAGE, USERBAN, CREATE_ROOM …
Response-сообщения могут быть следующих типов ACCEPTED, DENIED, ERROR
В ответ на каждый request будет отсылаться response с результатом обработки входящего сообщения.
Пример общения клиента с сервером:	
1)	Авторизация пользователя
	<message>
		<type>REQUEST</type>
		<status>AUTH</status>
		<login></login>
		<password></password>
		<date>16-01-2019 18:00</date>
	</message>

	<message>	
		<type>RESPONSE</type>
		<status>Accepted</status>
		<msg></msg>
<date>16-01-2019 18:01</date>
	</message>

2)	Регистрация пользователя
	<message>	
		<type>REQUEST</type>
		<status>REGISTRATION</status>
		<login>Misha</login>
		<password>12345678</password>
<date>17-01-2019 18:00</date>
	</message>

	<message>	
		<type>RESPONSE</type>
		<status>ACCEPTED</status>
		<msg></msg>
<date>17-01-2019 18:01</date>
	</message>

3)	Отправка сообщения
	<message>	
		<type>REQUEST</type>
		<status>MESSAGE</status>
		<fromIdPerson>1</fromIdPerson>
		<toIdRoom>12</toIdRoom>
<date>18-01-2019 18:00</date>
	</message>

	<message>	
		<type>RESPONSE</type>
		<status>DENIED</status>
		<msg></msg>
<date>18-01-2019 18:01</date>
	</message>
	


