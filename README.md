# Pokebowl (a Jobcoin mixer)

This is a Gemini Jobcoin interview project. The problem is to create a Jobcoin mixer - a user would deposit Jobcoins to a deposit address, and decide what addresses (plural) the Jobcoins would be sent to. The mixer would then transfer the Jobcoin from the deposit address to a house address, then from the house address disburses the desired amounts to the target addresses.

A server-side mixer was implemented in this project. The user can communcate to the mixer through http REST requests. The mixer creates the deposit address on behalf of the user for each mixing request, and owns both the deposit address and the house address. Only the mixer should be able to transfer Joboin out of the deposit address and the house address.

The implementation is in Scala. It uses akka-http and the actor system to serve REST endpoints and also perform mixing and disbursement.

# Mixer API
## POST /mixer/
### Description:  
Create a mixing request  

### Content-Type:  
application/json  

### Body:  
depositAmount - the amount to be mixed  
disbursements - array of disbursements
  * toAddress -  the address to disburse to
  * amount - the amount of Jobcoins to disburse  

All amounts in disbursements array should add up to depositAmount, 
and it is validated by the Mixer.

Example:
```
{
    "depositAmount":"0.3", 
    "disbursements": [
        {
            "toAddress": "Alice", 
            "amount": "0.1"
        },
        {
            "toAddress": "Bob", 
            "amount": "0.2"
        }
    ]
}
```

###Response:  
Statuses:  
201 (Created)  
400 (Bad request)

Response body:  
A deposit address that is generated for this mixing request. 
The user should then send a transaction to Jobcoin API to deposit to this address.  

Example:
```
{
    "depositAddress": "6341617d-accc-4f7a-8d88-f34aecc72711"
}
```


## POST /mixer/confirmDeposit/{depositAddress}
### Description: 
User should send this request to Mixer after depositing the correct amount to the deposit address, 
then the mixer will start the mixing and disbursement process.

### Body: 
empty

###Response:
Statuses:  
202 (Accepted): deposit is verified, and mixing will start asynchronously.  
400 (Bad request): if deposit address is not recognized, or does not contain the right amount of deposit

Response body:  
A description of whether the deposit validation was successful.

# Usage
The entry-point to start the server is in "QuickstartApp.scala", main function. So you can execute that from the IDE (e.g. IntelliJ) to start the server locally.

By default, the mixer runs at localhost:8082 and is configurable from "application.conf".

Once the server has started, you can use Postman to send request to localhost:8082 using the Mixer API listed above.
A Postman collection of the Mixer API and the Jobcoin API are attached in the project, under the "{project_root}/postman" folder.

1.  Send a Create Mixing request to Mixer API using Postman
    POST localhost:8082/mixer
    ```
    {
        "depositAmount":"0.3", 
        "disbursements": [
            {
                "toAddress": "Alice", 
                "amount": "0.1"
            },
            {
                "toAddress": "Bob", 
                "amount": "0.2"
            }
        ]
    }
    ```
    
    You should get a response like
    ```
    {
    "depositAddress": "6341617d-accc-4f7a-8d88-f34aecc72711"
    }
    ```

2.  Let's assume you own the address "Cat" which has already been created in the Jobcoin system, 
    send a transaction to transfer 0.3 Jobcoins from address "Cat" to the deposit address "6341617d-accc-4f7a-8d88-f34aecc72711"  
    POST http://jobcoin.gemini.com/lent-doornail/api/transactions
    ```
    {"fromAddress":"Cat", "toAddress":"6341617d-accc-4f7a-8d88-f34aecc72711", "amount":"0.3"}
    ```
     The deposit address should now have 0.3 Jobcoins.

3.  Tell Mixer server to confirm the deposit, and start mixing.  
    POST localhost:8082/mixer/confirmDeposit/6341617d-accc-4f7a-8d88-f34aecc72711

    The server should return 202 Accepted with a response like
    ```
    {"description": "Deposit address verified. Start mixing..."}
    ```
    
    The mixing is done asynchronously after the HTTP 202 is returned.  
    
    Using the [Jobcoin web UI](https://jobcoin.gemini.com/lent-doornail),
    
    * You should see a transaction of 0.3 from "6341617d-accc-4f7a-8d88-f34aecc72711" to "house"
    
    * You should see a transaction of 0.1 from "house" to "Alice", and 0.2 from "house" to "Bob".   
    
    * You should also see that the deposit address should show 0 amount, 
    
    * The "Alice" and "Bob" addresses should see increments of 0.1 and 0.2 respectively.

# Discussion
This project was written with great effort but has not reached production level quality.
I have been given the project for a week, and I do not want to delay submitting the project any longer.

There are several drawbacks which should be improved before going "live":

1.  The mixingMap (key=deposit address, value=mixing info) is held in memory.
    If the server goes down, the mixingMap is lost, and we lose track of the unprocessed money.
    We should use a database like postgres to hold the mixingMap so that we can preserve the mixing info.

2.  A delay should be introduced to the disbursement so that it becomes very difficult to
    attack the mixing by matching the disbursement time with the deposit time.

3.  Better error case handling is needed for the actor state machine. Perhaps adding re-try and confirmation
    by looking up the Jobcoin transaction has happened.
