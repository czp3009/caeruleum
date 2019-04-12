package com.hiczp.caeruleum.test

import com.hiczp.caeruleum.ServiceFunction

fun main() {
    ServiceFunction(GithubService::class, GithubService::withPathVariable)
}
