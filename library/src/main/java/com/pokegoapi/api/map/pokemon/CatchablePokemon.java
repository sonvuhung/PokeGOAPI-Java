/*
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.pokegoapi.api.map.pokemon;


import POGOProtos.Enums.EncounterTypeOuterClass.EncounterType;
import POGOProtos.Enums.PokemonIdOuterClass.PokemonId;
import POGOProtos.Inventory.Item.ItemIdOuterClass.ItemId;
import POGOProtos.Map.Fort.FortDataOuterClass.FortData;
import POGOProtos.Map.Pokemon.MapPokemonOuterClass.MapPokemon;
import POGOProtos.Map.Pokemon.WildPokemonOuterClass.WildPokemon;
import POGOProtos.Networking.Requests.Messages.CatchPokemonMessageOuterClass.CatchPokemonMessage;
import POGOProtos.Networking.Requests.Messages.DiskEncounterMessageOuterClass.DiskEncounterMessage;
import POGOProtos.Networking.Requests.Messages.EncounterMessageOuterClass.EncounterMessage;
import POGOProtos.Networking.Requests.Messages.IncenseEncounterMessageOuterClass.IncenseEncounterMessage;
import POGOProtos.Networking.Requests.Messages.UseItemCaptureMessageOuterClass.UseItemCaptureMessage;
import POGOProtos.Networking.Requests.RequestTypeOuterClass.RequestType;
import POGOProtos.Networking.Responses.CatchPokemonResponseOuterClass.CatchPokemonResponse;
import POGOProtos.Networking.Responses.CatchPokemonResponseOuterClass.CatchPokemonResponse.CatchStatus;
import POGOProtos.Networking.Responses.DiskEncounterResponseOuterClass.DiskEncounterResponse;
import POGOProtos.Networking.Responses.EncounterResponseOuterClass.EncounterResponse;
import POGOProtos.Networking.Responses.GetIncensePokemonResponseOuterClass.GetIncensePokemonResponse;
import POGOProtos.Networking.Responses.IncenseEncounterResponseOuterClass.IncenseEncounterResponse;
import POGOProtos.Networking.Responses.IncenseEncounterResponseOuterClass.IncenseEncounterResponse.Result;
import POGOProtos.Networking.Responses.UseItemCaptureResponseOuterClass.UseItemCaptureResponse;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.pokegoapi.api.PokemonGo;
import com.pokegoapi.api.inventory.Item;
import com.pokegoapi.api.inventory.ItemBag;
import com.pokegoapi.api.inventory.Pokeball;
import com.pokegoapi.api.listener.PokemonListener;
import com.pokegoapi.api.map.pokemon.encounter.DiskEncounterResult;
import com.pokegoapi.api.map.pokemon.encounter.EncounterResult;
import com.pokegoapi.api.map.pokemon.encounter.IncenseEncounterResult;
import com.pokegoapi.api.map.pokemon.encounter.NormalEncounterResult;
import com.pokegoapi.api.settings.AsyncCatchOptions;
import com.pokegoapi.api.settings.CatchOptions;
import com.pokegoapi.exceptions.AsyncCaptchaActiveException;
import com.pokegoapi.exceptions.AsyncLoginFailedException;
import com.pokegoapi.exceptions.AsyncRemoteServerException;
import com.pokegoapi.exceptions.CaptchaActiveException;
import com.pokegoapi.exceptions.EncounterFailedException;
import com.pokegoapi.exceptions.LoginFailedException;
import com.pokegoapi.exceptions.NoSuchItemException;
import com.pokegoapi.exceptions.RemoteServerException;
import com.pokegoapi.main.AsyncServerRequest;
import com.pokegoapi.util.AsyncHelper;
import com.pokegoapi.util.Log;
import com.pokegoapi.util.MapPoint;
import lombok.Getter;
import lombok.ToString;
import rx.Observable;
import rx.functions.Func1;

import java.util.List;


/**
 * The type Catchable pokemon.
 */
@ToString
public class CatchablePokemon implements MapPoint {

	private static final String TAG = CatchablePokemon.class.getSimpleName();
	private final PokemonGo api;
	@Getter
	private final String spawnPointId;
	@Getter
	private final long encounterId;
	@Getter
	private final PokemonId pokemonId;
	@Getter
	private final int pokemonIdValue;
	@Getter
	private final long expirationTimestampMs;
	@Getter
	private final double latitude;
	@Getter
	private final double longitude;
	private final EncounterKind encounterKind;
	private Boolean encountered = null;

	@Getter
	private double captureProbability;

	@Getter
	private boolean despawned = false;

	/**
	 * Instantiates a new Catchable pokemon.
	 *
	 * @param api the api
	 * @param proto the proto
	 */
	public CatchablePokemon(PokemonGo api, MapPokemon proto) {
		this.api = api;
		this.encounterKind = EncounterKind.NORMAL;
		this.spawnPointId = proto.getSpawnPointId();
		this.encounterId = proto.getEncounterId();
		this.pokemonId = proto.getPokemonId();
		this.pokemonIdValue = proto.getPokemonIdValue();
		this.expirationTimestampMs = proto.getExpirationTimestampMs();
		this.latitude = proto.getLatitude();
		this.longitude = proto.getLongitude();
	}


	/**
	 * Instantiates a new Catchable pokemon.
	 *
	 * @param api the api
	 * @param proto the proto
	 */
	public CatchablePokemon(PokemonGo api, WildPokemon proto) {
		this.api = api;
		this.encounterKind = EncounterKind.NORMAL;
		this.spawnPointId = proto.getSpawnPointId();
		this.encounterId = proto.getEncounterId();
		this.pokemonId = proto.getPokemonData().getPokemonId();
		this.pokemonIdValue = proto.getPokemonData().getPokemonIdValue();
		this.expirationTimestampMs = proto.getTimeTillHiddenMs();
		this.latitude = proto.getLatitude();
		this.longitude = proto.getLongitude();
	}

	/**
	 * Instantiates a new Catchable pokemon.
	 *
	 * @param api the api
	 * @param proto the proto
	 */
	public CatchablePokemon(PokemonGo api, FortData proto) {
		if (!proto.hasLureInfo()) {
			throw new IllegalArgumentException("Fort does not have lure");
		}
		this.api = api;
		// TODO: does this work?
		// seems that spawnPoint it's fortId in catchAPI so it should be safe to just set it in that way
		this.spawnPointId = proto.getLureInfo().getFortId();
		this.encounterId = proto.getLureInfo().getEncounterId();
		this.pokemonId = proto.getLureInfo().getActivePokemonId();
		this.pokemonIdValue = proto.getLureInfo().getActivePokemonIdValue();
		this.expirationTimestampMs = proto.getLureInfo()
				.getLureExpiresTimestampMs();
		this.latitude = proto.getLatitude();
		this.longitude = proto.getLongitude();
		this.encounterKind = EncounterKind.DISK;
	}

	/**
	 * Instantiates a new Catchable pokemon.
	 *
	 * @param api the api
	 * @param proto the proto
	 */
	public CatchablePokemon(PokemonGo api, GetIncensePokemonResponse proto) {
		this.api = api;
		this.spawnPointId = proto.getEncounterLocation();
		this.encounterId = proto.getEncounterId();
		this.pokemonId = proto.getPokemonId();
		this.pokemonIdValue = proto.getPokemonIdValue();
		this.expirationTimestampMs = proto.getDisappearTimestampMs();
		this.latitude = proto.getLatitude();
		this.longitude = proto.getLongitude();
		this.encounterKind = EncounterKind.INCENSE;
	}

	/**
	 * Encounter pokemon
	 *
	 * @return the encounter result
	 * @throws LoginFailedException the login failed exception
	 * @throws RemoteServerException the remote server exception
	 * @throws CaptchaActiveException if a captcha is active and the message can't be sent
	 */
	public EncounterResult encounterPokemon()
			throws LoginFailedException, CaptchaActiveException, RemoteServerException {
		return AsyncHelper.toBlocking(encounterPokemonAsync());
	}

	/**
	 * Encounter pokemon encounter result.
	 *
	 * @return the encounter result
	 */
	public Observable<EncounterResult> encounterPokemonAsync() {
		if (encounterKind == EncounterKind.NORMAL) {
			return encounterNormalPokemonAsync();
		} else if (encounterKind == EncounterKind.DISK) {
			return encounterDiskPokemonAsync();
		} else if (encounterKind == EncounterKind.INCENSE) {
			return encounterIncensePokemonAsync();
		}

		throw new IllegalStateException("Catchable pokemon missing encounter type");
	}

	/**
	 * Encounter pokemon encounter result.
	 *
	 * @return the encounter result
	 */
	public Observable<EncounterResult> encounterNormalPokemonAsync() {
		EncounterMessage reqMsg = EncounterMessage
				.newBuilder().setEncounterId(getEncounterId())
				.setPlayerLatitude(api.getLatitude())
				.setPlayerLongitude(api.getLongitude())
				.setSpawnPointId(getSpawnPointId()).build();
		AsyncServerRequest serverRequest = new AsyncServerRequest(
				RequestType.ENCOUNTER, reqMsg);
		return api.getRequestHandler()
				.sendAsyncServerRequests(serverRequest).map(new Func1<ByteString, EncounterResult>() {
					@Override
					public EncounterResult call(ByteString result) {
						EncounterResponse response;
						try {
							response = EncounterResponse
									.parseFrom(result);
						} catch (InvalidProtocolBufferException e) {
							throw new AsyncRemoteServerException(e);
						}
						encountered = response.getStatus() == EncounterResponse.Status.ENCOUNTER_SUCCESS;
						if (encountered) {
							List<PokemonListener> listeners = api.getListeners(PokemonListener.class);
							for (PokemonListener listener : listeners) {
								listener.onEncounter(api, getEncounterId(),
										CatchablePokemon.this, EncounterType.SPAWN_POINT);
							}
							CatchablePokemon.this.captureProbability
									= response.getCaptureProbability().getCaptureProbability(0);
						}
						return new NormalEncounterResult(api, response);
					}
				});
	}

	/**
	 * Encounter pokemon encounter result.
	 *
	 * @return the encounter result
	 * @throws LoginFailedException the login failed exception
	 * @throws RemoteServerException the remote server exception
	 * @throws CaptchaActiveException if a captcha is active and the message can't be sent
	 */
	public EncounterResult encounterNormalPokemon() throws LoginFailedException, CaptchaActiveException,
			RemoteServerException {
		return AsyncHelper.toBlocking(encounterNormalPokemonAsync());
	}

	/**
	 * Encounter pokemon
	 *
	 * @return the encounter result
	 */
	public Observable<EncounterResult> encounterDiskPokemonAsync() {
		DiskEncounterMessage reqMsg = DiskEncounterMessage
				.newBuilder().setEncounterId(getEncounterId())
				.setPlayerLatitude(api.getLatitude())
				.setPlayerLongitude(api.getLongitude())
				.setFortId(getSpawnPointId()).build();
		AsyncServerRequest serverRequest = new AsyncServerRequest(RequestType.DISK_ENCOUNTER, reqMsg);
		return api.getRequestHandler()
				.sendAsyncServerRequests(serverRequest).map(new Func1<ByteString, EncounterResult>() {
					@Override
					public EncounterResult call(ByteString result) {
						DiskEncounterResponse response;
						try {
							response = DiskEncounterResponse.parseFrom(result);
						} catch (InvalidProtocolBufferException e) {
							throw new AsyncRemoteServerException(e);
						}
						encountered = response.getResult() == DiskEncounterResponse.Result.SUCCESS;
						if (encountered) {
							List<PokemonListener> listeners = api.getListeners(PokemonListener.class);
							for (PokemonListener listener : listeners) {
								listener.onEncounter(api, getEncounterId(),
										CatchablePokemon.this, EncounterType.DISK);
							}
							CatchablePokemon.this.captureProbability
									= response.getCaptureProbability().getCaptureProbability(0);
						}
						return new DiskEncounterResult(api, response);
					}
				});
	}

	/**
	 * Encounter pokemon
	 *
	 * @return the encounter result
	 */
	public Observable<EncounterResult> encounterIncensePokemonAsync() {
		IncenseEncounterMessage reqMsg = IncenseEncounterMessage.newBuilder()
				.setEncounterId(getEncounterId())
				.setEncounterLocation(getSpawnPointId()).build();
		AsyncServerRequest serverRequest = new AsyncServerRequest(RequestType.INCENSE_ENCOUNTER, reqMsg);
		return api.getRequestHandler()
				.sendAsyncServerRequests(serverRequest).map(new Func1<ByteString, EncounterResult>() {
					@Override
					public EncounterResult call(ByteString result) {
						IncenseEncounterResponse response;
						try {
							response = IncenseEncounterResponse.parseFrom(result);
						} catch (InvalidProtocolBufferException e) {
							throw new AsyncRemoteServerException(e);
						}
						encountered = response.getResult() == Result.INCENSE_ENCOUNTER_SUCCESS;
						if (encountered) {
							List<PokemonListener> listeners = api.getListeners(PokemonListener.class);
							for (PokemonListener listener : listeners) {
								listener.onEncounter(api, getEncounterId(),
										CatchablePokemon.this, EncounterType.INCENSE);
							}
							CatchablePokemon.this.captureProbability
									= response.getCaptureProbability().getCaptureProbability(0);
						}
						return new IncenseEncounterResult(api, response);
					}
				});
	}

	/**
	 * Tries to catch a pokemon (using defined {@link CatchOptions}).
	 *
	 * @param options the CatchOptions object
	 * @return CatchResult
	 * @throws LoginFailedException if failed to login
	 * @throws RemoteServerException if the server failed to respond
	 * @throws CaptchaActiveException if a captcha is active and the message can't be sent
	 * @throws NoSuchItemException the no such item exception
	 */
	public CatchResult catchPokemon(CatchOptions options) throws LoginFailedException, CaptchaActiveException,
			RemoteServerException, NoSuchItemException {
		if (options == null) {
			options = new CatchOptions(api);
		}

		return catchPokemon(options.getNormalizedHitPosition(),
				options.getNormalizedReticleSize(),
				options.getSpinModifier(),
				options.selectPokeball(getUseablePokeballs(), captureProbability),
				options.getMaxPokeballs(),
				options.getRazzberries());
	}

	/**
	 * Tries to catch a pokemon (will attempt to use a pokeball if the capture probability greater than 50%, if you have
	 * none will use greatball etc).
	 *
	 * @param encounter the encounter to compare
	 * @param options the CatchOptions object
	 * @return the catch result
	 * @throws LoginFailedException the login failed exception
	 * @throws RemoteServerException the remote server exception
	 * @throws NoSuchItemException the no such item exception
	 * @throws CaptchaActiveException if a captcha is active and the message can't be sent
	 * @throws EncounterFailedException the encounter failed exception
	 */
	public CatchResult catchPokemon(EncounterResult encounter, CatchOptions options)
			throws LoginFailedException, EncounterFailedException, RemoteServerException,
			NoSuchItemException, CaptchaActiveException {

		if (!encounter.wasSuccessful()) throw new EncounterFailedException();
		double probability = encounter.getCaptureProbability().getCaptureProbability(0);

		if (options == null) {
			options = new CatchOptions(api);
		}

		return catchPokemon(options.getNormalizedHitPosition(),
				options.getNormalizedReticleSize(),
				options.getSpinModifier(),
				options.selectPokeball(getUseablePokeballs(), probability),
				options.getMaxPokeballs(),
				options.getRazzberries());
	}

	/**
	 * Tries to catch a pokemon (will attempt to use a pokeball, if you have
	 * none will use greatball etc).
	 *
	 * @return CatchResult
	 * @throws LoginFailedException if failed to login
	 * @throws RemoteServerException if the server failed to respond
	 * @throws NoSuchItemException the no such item exception
	 * @throws CaptchaActiveException if a captcha is active and the message can't be sent
	 */
	public CatchResult catchPokemon() throws LoginFailedException, CaptchaActiveException,
			RemoteServerException, NoSuchItemException {

		return catchPokemon(new CatchOptions(api));
	}

	/**
	 * Tries to catch a pokemon.
	 *
	 * @param normalizedHitPosition the normalized hit position
	 * @param normalizedReticleSize the normalized hit reticle
	 * @param spinModifier the spin modifier
	 * @param type Type of pokeball to throw
	 * @param amount Max number of Pokeballs to throw, negative number for unlimited
	 * @return CatchResult of resulted try to catch pokemon
	 * @throws LoginFailedException if failed to login
	 * @throws RemoteServerException if the server failed to respond
	 * @throws CaptchaActiveException if a captcha is active and the message can't be sent
	 */
	public CatchResult catchPokemon(double normalizedHitPosition,
									double normalizedReticleSize, double spinModifier, Pokeball type,
									int amount)
			throws LoginFailedException, CaptchaActiveException, RemoteServerException {

		return catchPokemon(normalizedHitPosition, normalizedReticleSize, spinModifier, type, amount, 0);
	}

	/**
	 * Tries to catch a pokemon (using defined {@link AsyncCatchOptions}).
	 *
	 * @param options the AsyncCatchOptions object
	 * @return Observable CatchResult
	 * @throws LoginFailedException if failed to login
	 * @throws RemoteServerException if the server failed to respond
	 * @throws NoSuchItemException the no such item exception
	 * @throws CaptchaActiveException if a captcha is active and the message can't be sent
	 */
	public Observable<CatchResult> catchPokemon(AsyncCatchOptions options)
			throws LoginFailedException, CaptchaActiveException, RemoteServerException, NoSuchItemException {
		if (options != null) {
			if (options.getUseRazzBerry() != 0) {
				final AsyncCatchOptions asyncOptions = options;
				final Pokeball asyncPokeball = asyncOptions.selectPokeball(getUseablePokeballs(), captureProbability);
				return useItemAsync(ItemId.ITEM_RAZZ_BERRY).flatMap(
						new Func1<CatchItemResult, Observable<CatchResult>>() {
							@Override
							public Observable<CatchResult> call(CatchItemResult result) {
								if (!result.getSuccess()) {
									return Observable.just(new CatchResult());
								}
								return catchPokemonAsync(asyncOptions.getNormalizedHitPosition(),
										asyncOptions.getNormalizedReticleSize(),
										asyncOptions.getSpinModifier(),
										asyncPokeball);
							}
						});
			}
		} else {
			options = new AsyncCatchOptions(api);
		}
		return catchPokemonAsync(options.getNormalizedHitPosition(),
				options.getNormalizedReticleSize(),
				options.getSpinModifier(),
				options.selectPokeball(getUseablePokeballs(), captureProbability));
	}

	/**
	 * Tries to catch a pokemon (will attempt to use a pokeball if the capture probability greater than 50%, if you have
	 * none will use greatball etc).
	 *
	 * @param encounter the encounter to compare
	 * @param options the CatchOptions object
	 * @return the catch result
	 * @throws LoginFailedException the login failed exception
	 * @throws RemoteServerException the remote server exception
	 * @throws NoSuchItemException the no such item exception
	 * @throws CaptchaActiveException the encounter failed exception
	 * @throws CaptchaActiveException if a captcha is active and the message can't be sent
	 */
	public Observable<CatchResult> catchPokemon(EncounterResult encounter,
												AsyncCatchOptions options)
			throws LoginFailedException, EncounterFailedException, RemoteServerException,
			NoSuchItemException, CaptchaActiveException {

		if (!encounter.wasSuccessful()) throw new EncounterFailedException();

		if (options != null) {
			if (options.getUseRazzBerry() != 0) {
				final AsyncCatchOptions asyncOptions = options;
				final Pokeball asyncPokeball = asyncOptions.selectPokeball(getUseablePokeballs(), captureProbability);
				return useItemAsync(ItemId.ITEM_RAZZ_BERRY).flatMap(
						new Func1<CatchItemResult, Observable<CatchResult>>() {
							@Override
							public Observable<CatchResult> call(CatchItemResult result) {
								if (!result.getSuccess()) {
									return Observable.just(new CatchResult());
								}
								return catchPokemonAsync(asyncOptions.getNormalizedHitPosition(),
										asyncOptions.getNormalizedReticleSize(),
										asyncOptions.getSpinModifier(),
										asyncPokeball);
							}
						});
			}
		} else {
			options = new AsyncCatchOptions(api);
		}
		return catchPokemonAsync(options.getNormalizedHitPosition(),
				options.getNormalizedReticleSize(),
				options.getSpinModifier(),
				options.selectPokeball(getUseablePokeballs(), captureProbability));
	}

	/**
	 * Tries to catch a pokemon.
	 *
	 * @param normalizedHitPosition the normalized hit position
	 * @param normalizedReticleSize the normalized hit reticle
	 * @param spinModifier the spin modifier
	 * @param type Type of pokeball to throw
	 * @param amount Max number of Pokeballs to throw, negative number for unlimited
	 * @param razberriesLimit The maximum amount of razberries to use, -1 for unlimited
	 * @return CatchResult of resulted try to catch pokemon
	 * @throws LoginFailedException if failed to login
	 * @throws RemoteServerException if the server failed to respond
	 * @throws CaptchaActiveException if a captcha is active and the message can't be sent
	 */
	public CatchResult catchPokemon(double normalizedHitPosition,
									double normalizedReticleSize, double spinModifier, Pokeball type,
									int amount, int razberriesLimit)
			throws LoginFailedException, CaptchaActiveException, RemoteServerException {

		ItemBag itemBag = api.getInventories().getItemBag();
		Item razzberriesInventory = itemBag.getItem(ItemId.ITEM_RAZZ_BERRY);
		int razzberriesCountInventory = razzberriesInventory.getCount();
		int razberries = 0;
		int numThrows = 0;
		Item pokeballItem = itemBag.getItem(type.getBallType());
		int pokeballCount = pokeballItem.getCount();
		CatchResult result;

		if (razzberriesCountInventory < razberriesLimit) {
			razberriesLimit = razzberriesCountInventory;
		}

		do {
			if ((razberries < razberriesLimit || razberriesLimit == -1)
					&& useItem(ItemId.ITEM_RAZZ_BERRY).getSuccess()) {

				razberries++;
				razzberriesCountInventory--;

				razzberriesInventory.setCount(razzberriesCountInventory);
			}
			result = AsyncHelper.toBlocking(catchPokemonAsync(normalizedHitPosition,
					normalizedReticleSize, spinModifier, type));
			if (result == null) {
				Log.wtf(TAG, "Got a null result after catch attempt");
				break;
			}

			if (result.getStatus() != CatchStatus.CATCH_ERROR) {
				pokeballItem.setCount(--pokeballCount);
				if (pokeballCount <= 0) {
					break;
				}
			}

			// continue for the following cases:
			// CatchStatus.CATCH_ESCAPE
			// CatchStatus.CATCH_MISSED
			// covers all cases

			// if its caught of has fleed, end the loop
			// FLEE OR SUCCESS
			if (result.getStatus() == CatchStatus.CATCH_FLEE
					|| result.getStatus() == CatchStatus.CATCH_SUCCESS) {
				Log.v(TAG, "Pokemon caught/or flee");
				break;
			}
			// if error or unrecognized end the loop
			// ERROR OR UNRECOGNIZED
			if (result.getStatus() == CatchStatus.CATCH_ERROR
					|| result.getStatus() == CatchStatus.UNRECOGNIZED) {
				Log.wtf(TAG, "Got an error or unrecognized catch attempt");
				Log.wtf(TAG, "Proto:" + result);
				break;
			}

			boolean abort = false;

			List<PokemonListener> listeners = api.getListeners(PokemonListener.class);
			for (PokemonListener listener : listeners) {
				abort |= listener.onCatchEscape(api, this, type, numThrows);
			}

			if (abort) {
				break;
			}

			numThrows++;

		}
		while (amount < 0 || numThrows < amount);

		return result;
	}

	/**
	 * Tries to catch a pokemon.
	 *
	 * @param normalizedHitPosition the normalized hit position
	 * @param normalizedReticleSize the normalized hit reticle
	 * @param spinModifier the spin modifier
	 * @param type Type of pokeball to throw
	 * @return CatchResult of resulted try to catch pokemon
	 */
	public Observable<CatchResult> catchPokemonAsync(
			double normalizedHitPosition, double normalizedReticleSize, double spinModifier, Pokeball type) {
		if (!isEncountered()) {
			return Observable.just(new CatchResult());
		}

		CatchPokemonMessage reqMsg = CatchPokemonMessage.newBuilder()
				.setEncounterId(getEncounterId()).setHitPokemon(true)
				.setNormalizedHitPosition(normalizedHitPosition)
				.setNormalizedReticleSize(normalizedReticleSize)
				.setSpawnPointId(getSpawnPointId())
				.setSpinModifier(spinModifier)
				.setPokeball(type.getBallType()).build();
		AsyncServerRequest serverRequest = new AsyncServerRequest(
				RequestType.CATCH_POKEMON, reqMsg);
		return catchPokemonAsync(serverRequest);
	}

	private Observable<CatchResult> catchPokemonAsync(AsyncServerRequest serverRequest) {
		return api.getRequestHandler().sendAsyncServerRequests(serverRequest).map(new Func1<ByteString, CatchResult>() {
			@Override
			public CatchResult call(ByteString result) {
				CatchPokemonResponse response;

				try {
					response = CatchPokemonResponse.parseFrom(result);
				} catch (InvalidProtocolBufferException e) {
					throw new AsyncRemoteServerException(e);
				}
				try {

					// pokemon is caught or flee, and no longer on the map
					if (response.getStatus() == CatchStatus.CATCH_FLEE
							|| response.getStatus() == CatchStatus.CATCH_SUCCESS) {
						despawned = true;
					}

					api.getInventories().updateInventories();
					return new CatchResult(response);
				} catch (RemoteServerException e) {
					throw new AsyncRemoteServerException(e);
				} catch (LoginFailedException e) {
					throw new AsyncLoginFailedException(e);
				} catch (CaptchaActiveException e) {
					throw new AsyncCaptchaActiveException(e, e.getCaptcha());
				}
			}
		});
	}

	private List<Pokeball> getUseablePokeballs() {
		return api.getInventories().getItemBag().getUseablePokeballs();
	}

	/**
	 * Tries to use an item on a catchable pokemon (ie razzberry).
	 *
	 * @param item the item ID
	 * @return CatchItemResult info about the new modifiers about the pokemon (can move, item capture multi) eg
	 */
	public Observable<CatchItemResult> useItemAsync(ItemId item) {
		UseItemCaptureMessage reqMsg = UseItemCaptureMessage
				.newBuilder()
				.setEncounterId(this.getEncounterId())
				.setSpawnPointId(this.getSpawnPointId())
				.setItemId(item)
				.build();

		AsyncServerRequest serverRequest = new AsyncServerRequest(
				RequestType.USE_ITEM_CAPTURE, reqMsg);
		return api.getRequestHandler()
				.sendAsyncServerRequests(serverRequest).map(new Func1<ByteString, CatchItemResult>() {
					@Override
					public CatchItemResult call(ByteString result) {
						UseItemCaptureResponse response;
						try {
							response = UseItemCaptureResponse.parseFrom(result);
						} catch (InvalidProtocolBufferException e) {
							throw new AsyncRemoteServerException(e);
						}
						return new CatchItemResult(response);
					}
				});
	}

	/**
	 * Tries to use an item on a catchable pokemon (ie razzberry).
	 *
	 * @param item the item ID
	 * @return CatchItemResult info about the new modifiers about the pokemon (can move, item capture multi) eg
	 * @throws LoginFailedException if failed to login
	 * @throws RemoteServerException if the server failed to respond
	 * @throws CaptchaActiveException if a captcha is active and the message can't be sent
	 */
	public CatchItemResult useItem(ItemId item)
			throws LoginFailedException, CaptchaActiveException, RemoteServerException {
		return AsyncHelper.toBlocking(useItemAsync(item));
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		} else if (obj instanceof CatchablePokemon) {
			return this.getEncounterId() == ((CatchablePokemon) obj)
					.getEncounterId();
		}
		return false;
	}

	@Override
	public int hashCode() {
		return (int) this.getEncounterId();
	}

	/**
	 * Encounter check
	 *
	 * @return Checks if encounter has happened
	 */
	public boolean isEncountered() {
		if (encountered == null) {
			return false;
		}
		return encountered;
	}

	/**
	 * Return true when the catchable pokemon is a lured pokemon
	 *
	 * @return true for lured pokemon
	 */
	public boolean isLured() {
		return encounterKind == EncounterKind.DISK;
	}

	/**
	 * Return true when the catchable pokemon is a lured pokemon from incense
	 *
	 * @return true for pokemon lured by incense
	 */
	public boolean isFromIncense() {
		return encounterKind == EncounterKind.INCENSE;
	}

	private enum EncounterKind {
		NORMAL,
		DISK,
		INCENSE;
	}
}
