package com.geoffreymak

import com.typesafe.config.ConfigFactory

object TestActorSystemConfig {
  val actorSystemConfig = ConfigFactory.parseString(
    s"""
    akka {
      actor {
        default-dispatcher {
          type = Dispatcher
          executor = "thread-pool-executor"
          thread-pool-executor {
            fixed-pool-size = 32
          }
          throughput = 1
        }
      }
    }
    pokebowl {
      jobcoin {
          apiAddressesUrl = "http://jobcoin.gemini.com/lent-doornail/api/addresses"
          apiTransactionsUrl = "http://jobcoin.gemini.com/lent-doornail/api/transactions"
      }
    }
    """
  )
}